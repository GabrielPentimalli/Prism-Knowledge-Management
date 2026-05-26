#!/usr/bin/env python3
"""
PRISM RAG diagnostic harness.

Riproduce fedelmente la pipeline di chat di PRISM
(retrieval -> synthesis -> verification) per capire PERCHE' il modello
risponde "non ho evidenza sufficiente nei documenti caricati".

Per ogni domanda registra cosa succede a ogni stadio e classifica
lo stadio esatto in cui la risposta fallisce, cosi' da distinguere:
  - retrieval vuoto              (nessun chunk recuperato)
  - LLM ha risposto vuoto        (Ollama non ha prodotto testo)
  - JSON non parsabile           (output non conforme allo schema)
  - LLM dice "insufficiente"     (il modello stesso si rifiuta)
  - nessuna citazione            (risposta valida ma 0 citations -> scartata dalla verifica)
  - citazioni invalide           (chunkId non ricopiati esattamente -> tutte scartate)
  - OK                           (risposta + almeno una citazione valida)

Non richiede di avviare l'app Spring: legge i chunk dai JSON in prism-data,
approssima il retrieval Lucene con un BM25 leggero + il bilanciamento per-vault,
e chiama lo stesso endpoint Ollama con lo STESSO prompt di SynthesisAgentService.
"""

import argparse
import glob
import json
import math
import os
import re
import time
import urllib.request
from collections import Counter, defaultdict

# --- Parametri allineati a application.properties ---
LLM_BASE_URL = os.environ.get("PRISM_LLM_URL", "http://localhost:11434/v1")
LLM_MODEL = os.environ.get("PRISM_LLM_MODEL", "mistral-nemo:12b")
TOP_K = 8
MIN_PER_DOC = 2
VAULT_PER_DOC_CAP = 4

# Prompt copiato VERBATIM da SynthesisAgentService.PROMPT
SYNTHESIS_PROMPT = """Sei il synthesis agent di PRISM. Il tuo compito è rispondere alla domanda dell'utente
usando ESCLUSIVAMENTE i chunk forniti come evidenza, citandoli con il loro chunkId.

Regole:
1. Non inventare informazioni non presenti nei chunk.
2. Per domande comparative (es. "cosa hanno in comune?", "differenze?"), analizza chunk
   provenienti da TUTTI i documenti citati e produci un confronto strutturato.
3. Per domande di sintesi, riassumi i chunk pertinenti.
4. Per domande puntuali, rispondi citando i chunk specifici.
5. Cita SEMPRE almeno un chunkId presente nell'elenco fornito, anche per risposte brevi.
6. Se i chunk forniti non contengono alcuna informazione rilevante per rispondere,
   imposta answer esattamente a "non ho evidenza sufficiente nei documenti caricati"
   e restituisci citations come array vuoto.

Rispondi SOLO in JSON valido con questo schema, senza markdown, senza spiegazioni:
{
  "answer": "testo risposta in italiano",
  "citations": [
    {"docId":"...", "docName":"...", "page":1, "paragraphIndex":2, "chunkId":"...", "score":0.9}
  ]
}
"""

INSUFFICIENT = "non ho evidenza sufficiente nei documenti caricati"

FORCE_JSON = False   # impostato da --force-json per l'esperimento A/B
SIMPLE_CITE = False  # impostato da --simple-cite per l'esperimento schema-semplice

# Prompt alternativo dell'esperimento: schema minimale + citazioni come indici interi.
SIMPLE_PROMPT = """Sei l'assistente di PRISM. Rispondi SEMPRE in ITALIANO basandoti ESCLUSIVAMENTE
sui frammenti numerati forniti dall'utente. Indica le fonti con i NUMERI dei frammenti usati.

Devi restituire un oggetto JSON con ESATTAMENTE due campi, senza aggiungerne altri:
  - "answer": stringa, la risposta in italiano
  - "sources": array di numeri interi, gli indici dei frammenti citati (es. [1, 3])

Esempio di output valido:
{"answer": "La depressione aumenta il rischio di suicidio.", "sources": [1, 2]}

Non usare nomi di campo diversi da "answer" e "sources". Niente markdown, niente testo fuori dal JSON.
Se i frammenti non contengono informazioni utili, rispondi con
{"answer": "non ho evidenza sufficiente nei documenti caricati", "sources": []}.
"""

# Chiavi alternative tollerate quando il modello devia dallo schema.
ANSWER_KEYS = ("answer", "risposta", "response", "text", "synthesized_text", "summary", "content")
SOURCE_KEYS = ("sources", "source", "citations", "cited", "fragments", "indices")

# Domande di test, raggruppate per categoria, ricalcate sulle chat reali fallite
TEST_QUESTIONS = [
    ("meta",        "quanti documenti ho caricato nel vault?"),
    ("summary",     'riassumi il documento "A review of depression and suicide risk assessment using speech analysis"'),
    ("summary-all", "scrivi una frase di riassunto per ogni documento nel vault"),
    ("content",     "di cosa parla il documento sul riconoscimento del disgusto facciale?"),
    ("content-en",  "What is the role of speech analysis in suicide risk assessment?"),
    ("factual",     "qual è il journal in cui è pubblicato l'articolo sul facial disgust?"),
    ("comparative", "cosa hanno in comune i tre documenti del vault?"),
    ("keyword",     "depression suicide risk speech analysis"),
]

WORD_RE = re.compile(r"[a-zA-Z0-9]+")


def tokenize(text):
    return [t.lower() for t in WORD_RE.findall(text or "")]


def load_chunks(data_root):
    """Carica tutti i chunk dei documenti del vault dai file chunks.json."""
    docs = {}
    for path in glob.glob(os.path.join(data_root, "documents", "*", "chunks.json")):
        chunks = json.load(open(path, encoding="utf-8"))
        for c in chunks:
            doc_id = c["docId"]
            docs.setdefault(doc_id, []).append({
                "docId": doc_id,
                "chunkId": c["chunkId"],
                "docName": c.get("metadata", {}).get("docName", ""),
                "text": c.get("text", ""),
                "page": c.get("pageNumber", 1),
                "paragraphIndex": c.get("paragraphIndex", 0),
            })
    for doc_id in docs:
        docs[doc_id].sort(key=lambda c: c["paragraphIndex"])
    return docs


def build_bm25(docs):
    """Indice BM25 leggero su tutti i chunk (approssima Lucene su campo 'text')."""
    all_chunks = [c for cs in docs.values() for c in cs]
    N = len(all_chunks)
    df = Counter()
    tokenized = []
    for c in all_chunks:
        toks = tokenize(c["text"])
        tokenized.append(toks)
        for term in set(toks):
            df[term] += 1
    avgdl = sum(len(t) for t in tokenized) / max(1, N)
    return {"chunks": all_chunks, "tokenized": tokenized, "df": df, "N": N, "avgdl": avgdl}


def bm25_search(index, query, top_k, k1=1.2, b=0.75):
    q_terms = set(tokenize(query))
    scored = []
    for i, c in enumerate(index["chunks"]):
        toks = index["tokenized"][i]
        if not toks:
            continue
        tf = Counter(toks)
        dl = len(toks)
        score = 0.0
        for term in q_terms:
            if term not in tf:
                continue
            idf = math.log(1 + (index["N"] - index["df"][term] + 0.5) / (index["df"][term] + 0.5))
            denom = tf[term] + k1 * (1 - b + b * dl / index["avgdl"])
            score += idf * (tf[term] * (k1 + 1)) / denom
        if score > 0:
            scored.append((score, c))
    scored.sort(key=lambda x: x[0], reverse=True)
    return [{**c, "score": s} for s, c in scored[:top_k]]


def retrieve_vault(index, docs, query):
    """Replica RetrievalAgentService per scope VAULT: BM25 + bilanciamento per-doc."""
    doc_ids = set(docs.keys())
    effective_top_k = max(TOP_K, len(doc_ids) * VAULT_PER_DOC_CAP)
    text_hits = bm25_search(index, query, effective_top_k)

    count_per_doc = Counter(h["docId"] for h in text_hits)
    under = [d for d in doc_ids if count_per_doc.get(d, 0) < MIN_PER_DOC]
    if not under:
        return text_hits, "bm25"

    seen = {h["chunkId"] for h in text_hits}
    combined = list(text_hits)
    for d in under:
        for c in docs[d][:MIN_PER_DOC]:
            if c["chunkId"] not in seen:
                seen.add(c["chunkId"])
                combined.append({**c, "score": 0.0})
    return combined, f"bm25+balance({len(under)} doc)"


def build_payload(user_message, chunks):
    """Replica SynthesisAgentService.buildPayload."""
    sb = [f"Domanda utente: {user_message}\n\n"]
    names = []
    for c in chunks:
        if c["docName"] not in names:
            names.append(c["docName"])
    sb.append("Documenti rappresentati nei chunk: " + ", ".join(names))
    sb.append("\n\nChunk disponibili:\n")
    for h in chunks:
        sb.append("---\n")
        sb.append(f"chunkId: {h['chunkId']}\n")
        sb.append(f"docId: {h['docId']}\n")
        sb.append(f"docName: {h['docName']}\n")
        sb.append(f"page: {h['page']}\n")
        sb.append(f"paragraphIndex: {h['paragraphIndex']}\n")
        sb.append(f"score: {h['score']}\n")
        sb.append(f"text: {h['text']}\n")
    sb.append("---\n")
    return "".join(sb)


def build_simple_payload(user_message, chunks):
    """Payload dell'esperimento: frammenti numerati [1..N], niente chunkId lunghi."""
    sb = [f"Domanda: {user_message}\n\nFrammenti:\n"]
    for i, h in enumerate(chunks, start=1):
        sb.append(f"[{i}] (doc: {h['docName']}) {h['text']}\n\n")
    return "".join(sb)


def _find_answer(node):
    """Cerca un testo-risposta tra chiavi tollerate (anche annidate)."""
    if isinstance(node, dict):
        for k in ANSWER_KEYS:
            if k in node and isinstance(node[k], str) and node[k].strip():
                return node[k].strip()
        for v in node.values():
            found = _find_answer(v)
            if found:
                return found
    return None


def _find_sources(node):
    """Estrae indici interi da chiavi tollerate, coercendo vari formati."""
    if isinstance(node, dict):
        for k in SOURCE_KEYS:
            if k in node:
                return _coerce_ints(node[k])
        for v in node.values():
            s = _find_sources(v)
            if s:
                return s
    return []


def _coerce_ints(value):
    out = []
    if isinstance(value, list):
        for item in value:
            if isinstance(item, bool):
                continue
            if isinstance(item, int):
                out.append(item)
            elif isinstance(item, str) and item.strip().isdigit():
                out.append(int(item.strip()))
            elif isinstance(item, dict):
                for kk in ("index", "n", "id", "source"):
                    if isinstance(item.get(kk), int):
                        out.append(item[kk])
                        break
    return out


def verify_simple(candidate, chunks):
    """Verifica per lo schema a indici: answer tollerante + sources interi -> chunk reali."""
    if candidate is None:
        return INSUFFICIENT, [], "candidato nullo"
    answer = _find_answer(candidate)
    if not answer:
        return INSUFFICIENT, [], "answer vuoto"
    if answer.strip().lower() == INSUFFICIENT:
        return INSUFFICIENT, [], "LLM dice insufficiente"
    verified = []
    for idx in _find_sources(candidate):
        if 1 <= idx <= len(chunks):
            verified.append(chunks[idx - 1])
    # Degradazione graziosa: una risposta valida senza fonti esplicite resta valida.
    if not verified:
        return answer, [], "risposta valida ma 0 sources"
    return answer, verified, "ok"


def llm_chat(system_prompt, user_message, temperature=0.0, force_json=False):
    payload = {
        "model": LLM_MODEL,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_message},
        ],
        "temperature": temperature,
    }
    if force_json:
        # Forza output JSON (OpenAI-compatible, supportato da Ollama).
        payload["response_format"] = {"type": "json_object"}
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        LLM_BASE_URL + "/chat/completions",
        data=body,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=300) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    return data["choices"][0]["message"]["content"]


def extract_json(raw):
    """Replica SynthesisAgentService.extractJson. Ritorna (json_text, fell_back).

    fell_back=True quando il raw NON conteneva un oggetto JSON e quindi
    e' stato sostituito il fallback "insufficiente" (anche se l'LLM aveva
    risposto bene in prosa)."""
    if not raw or not raw.strip():
        return '{"answer":"%s","citations":[]}' % INSUFFICIENT, True
    cleaned = raw.strip().replace("```json", "").replace("```", "").strip()
    start = cleaned.find("{")
    end = cleaned.rfind("}")
    if start >= 0 and end > start:
        return cleaned[start:end + 1], False
    return '{"answer":"%s","citations":[]}' % INSUFFICIENT, True


def verify(candidate, retrieval_hits):
    """Replica VerificationAgentService.verify. Ritorna (answer, valid_citations, reason)."""
    if candidate is None:
        return INSUFFICIENT, [], "candidato nullo"
    answer = candidate.get("answer")
    if not answer or not answer.strip():
        return INSUFFICIENT, [], "answer vuoto"
    valid_ids = {h["chunkId"] for h in retrieval_hits}
    citations = candidate.get("citations") or []
    if not citations:
        return INSUFFICIENT, [], "nessuna citation prodotta"
    verified = [c for c in citations if c.get("chunkId") in valid_ids]
    if not verified:
        return INSUFFICIENT, [], "tutte le citation rifiutate (chunkId non in retrieval)"
    return answer, verified, "ok"


def classify_invalid_chunkids(proposed, valid_ids):
    """Per i chunkId proposti ma non validi, capisce se sono troncati/inventati."""
    notes = []
    for cid in proposed:
        if cid in valid_ids:
            continue
        if cid is None:
            notes.append("(null)")
            continue
        # match per prefisso: docId corretto ma indice/forma sbagliata?
        near = [v for v in valid_ids if v.startswith(cid[:36])]
        if near:
            notes.append(f"{cid!r} ~troncato/variato (docId esiste)")
        else:
            notes.append(f"{cid!r} inventato")
    return notes


def run(data_root, repeat, out_path):
    docs = load_chunks(data_root)
    index = build_bm25(docs)
    print(f"Caricati {sum(len(v) for v in docs.values())} chunk da {len(docs)} documenti.\n")

    stage_counter = Counter()
    rows = []

    for category, question in TEST_QUESTIONS:
        for run_i in range(repeat):
            hits, retr_mode = retrieve_vault(index, docs, question)
            valid_ids = {h["chunkId"] for h in hits}

            if not hits:
                stage = "RETRIEVAL_EMPTY"
                row = dict(category=category, question=question, run=run_i, retr_mode=retr_mode,
                           n_hits=0, raw="", json_ok=False, answer="", n_prop=0, n_valid=0,
                           stage=stage, verify_reason="retrieval vuoto", invalid_notes=[])
                stage_counter[stage] += 1
                rows.append(row)
                _print_row(row)
                continue

            if SIMPLE_CITE:
                payload = build_simple_payload(question, hits)
                prompt = SIMPLE_PROMPT
            else:
                payload = build_payload(question, hits)
                prompt = SYNTHESIS_PROMPT
            t0 = time.time()
            try:
                raw = llm_chat(prompt, payload, force_json=(FORCE_JSON or SIMPLE_CITE))
            except Exception as e:
                raw = ""
                print(f"  [errore LLM] {e}")
            dt = time.time() - t0

            json_text, fell_back = extract_json(raw)
            json_ok = True
            candidate = None
            try:
                candidate = json.loads(json_text)
            except Exception:
                json_ok = False

            # L'LLM ha comunque prodotto del testo non vuoto e non-fallback?
            prose_ok = bool(raw and raw.strip())

            if not prose_ok:
                stage = "LLM_EMPTY"
                answer, valid, reason = INSUFFICIENT, [], "LLM ha risposto vuoto"
            elif fell_back:
                # L'LLM ha risposto MA non in JSON -> parser scarta tutto e mette il fallback.
                # Spesso la risposta in prosa era valida: e' un fallimento di FORMATO.
                stage = "LLM_NO_JSON"
                answer, valid, reason = INSUFFICIENT, [], "output non-JSON, fallback forzato"
            elif not json_ok:
                stage = "JSON_UNPARSABLE"
                answer, valid, reason = INSUFFICIENT, [], "JSON non parsabile"
            elif SIMPLE_CITE:
                answer, valid, reason = verify_simple(candidate, hits)
                if reason == "ok":
                    stage = "OK"
                elif reason == "risposta valida ma 0 sources":
                    stage = "OK_NO_SOURCES"
                elif reason == "LLM dice insufficiente":
                    stage = "LLM_SAID_INSUFFICIENT"
                else:
                    stage = "OTHER_INSUFFICIENT"
            else:
                answer, valid, reason = verify(candidate, hits)
                cand_ans = (candidate.get("answer") or "").strip().lower()
                if cand_ans == INSUFFICIENT:
                    stage = "LLM_SAID_INSUFFICIENT"
                elif reason == "nessuna citation prodotta":
                    stage = "NO_CITATIONS"
                elif reason == "tutte le citation rifiutate (chunkId non in retrieval)":
                    stage = "CITATIONS_INVALID"
                elif reason == "ok":
                    stage = "OK"
                else:
                    stage = "OTHER_INSUFFICIENT"

            proposed = [c.get("chunkId") for c in (candidate.get("citations") or [])] if candidate else []
            invalid_notes = classify_invalid_chunkids(proposed, valid_ids)

            row = dict(category=category, question=question, run=run_i, retr_mode=retr_mode,
                       n_hits=len(hits), raw=raw, json_ok=json_ok, fell_back=fell_back,
                       answer=(candidate.get("answer") if candidate else "") or "",
                       n_prop=len(proposed), n_valid=len(valid), stage=stage,
                       verify_reason=reason, invalid_notes=invalid_notes, latency=round(dt, 1))
            stage_counter[stage] += 1
            rows.append(row)
            _print_row(row)

    print("\n" + "=" * 70)
    print("RIEPILOGO PER STADIO DI FALLIMENTO")
    print("=" * 70)
    total = sum(stage_counter.values())
    for stage, n in stage_counter.most_common():
        print(f"  {stage:24s} {n:3d}  ({100*n/total:.0f}%)")
    ok = stage_counter.get("OK", 0)
    print(f"\n  Risposte valide: {ok}/{total} ({100*ok/total:.0f}%)")

    if out_path:
        os.makedirs(os.path.dirname(out_path), exist_ok=True)
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump({"summary": dict(stage_counter), "rows": rows}, f, ensure_ascii=False, indent=2)
        print(f"\nReport salvato in {out_path}")


def _print_row(r):
    print(f"[{r['category']:11s}] run{r['run']} | {r['stage']:22s} | hits={r['n_hits']:2d} "
          f"({r['retr_mode']}) | cit prop={r['n_prop']} valid={r['n_valid']}")
    print(f"             Q: {r['question'][:80]}")
    if r.get('fell_back') and r.get('raw'):
        prose = r['raw'].strip().replace("\n", " ")
        print(f"             >> LLM HA RISPOSTO (ma non in JSON): {prose[:110]}")
    elif r['answer'] and r['stage'] != 'OK':
        print(f"             answer LLM: {r['answer'][:90]}")
    if r['invalid_notes']:
        for n in r['invalid_notes'][:3]:
            print(f"             chunkId KO: {n}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--data-root", default=os.path.join(os.path.dirname(__file__), "..", "lucene", "prism-data"))
    ap.add_argument("--repeat", type=int, default=1, help="ripetizioni per domanda (mostra non-determinismo)")
    ap.add_argument("--output", default=os.path.join(os.path.dirname(__file__), "..", "output", "rag_diagnostic.json"))
    ap.add_argument("--force-json", action="store_true",
                    help="A/B: forza response_format json_object verso Ollama")
    ap.add_argument("--simple-cite", action="store_true",
                    help="Esperimento: schema minimale {answer, sources:[int]} + frammenti numerati")
    args = ap.parse_args()
    FORCE_JSON = args.force_json
    SIMPLE_CITE = args.simple_cite
    if SIMPLE_CITE:
        print(">>> ESPERIMENTO: schema semplice (answer + sources interi) + JSON forzato\n")
    elif FORCE_JSON:
        print(">>> MODALITA' A/B: response_format=json_object ATTIVO\n")
    run(os.path.normpath(args.data_root), args.repeat, os.path.normpath(args.output))
