# Strumenti di supporto PRISM

L'ingestione e la ricerca dell'applicazione sono implementate in Java nel modulo `lucene/`. Questa cartella contiene strumenti separati, con scopi e limiti diversi:

| File | Scopo | Relazione con l'applicazione corrente |
|---|---|---|
| `ollama_benchmark.py` | Confronto dei modelli attraverso `/api/chat` | Usa il contratto API attuale; richiede uno scope già indicizzato. |
| `test_ollama_benchmark.py` | Regressioni dei contratti e del ripristino impostazioni | Simula HTTP e processi; non misura un modello reale. |
| `rag_diagnostic.py` | Esperimenti su prompt, formato delle fonti e BM25 approssimato | Non riproduce la pipeline Java: carica tutti i documenti del data root, usa prompt e verifiche sperimentali/legacy e non esegue la riscrittura conversazionale. |
| `query_test.py` | Automazione Selenium della precedente UI per articoli, tabelle e immagini | Legacy: i suoi selettori non appartengono all'interfaccia PRISM corrente; non è una suite end-to-end utilizzabile su questa versione. |

Non sono presenti script di download HTML o di estrazione separata di tabelle e immagini.

## Benchmark comparativo Ollama

Script: `scripts/ollama_benchmark.py`

Confronta i modelli installati in Ollama sulla stessa domanda fissa, usando un documento o vault già caricato in PRISM.

### Prerequisiti

- Ollama in esecuzione (`http://localhost:11434`) e almeno due modelli scaricati.
- JDK 21, Maven 3.8 o successivo e Python 3.9 o successivo.
- Un documento o vault già indicizzato: annotarne l'ID dall'applicazione.
- Backend PRISM arrestato e porta 8080 libera: lo script lo avvia e arresta per ogni esecuzione.
- (Opzionale) `matplotlib` per includere i grafici nel report HTML.

### Esecuzione

```bash
python scripts/ollama_benchmark.py --scope-type DOCUMENT --scope-id <document-id>
# Oppure:
python scripts/ollama_benchmark.py --scope-type VAULT --scope-id <vault-id>
```

Lo script seleziona ogni modello tramite l'API delle impostazioni, così la configurazione persistita non prevale sul modello da misurare. Ripristina il modello persistito al termine di ogni esecuzione e le properties originali al termine del benchmark. Crea normali sessioni chat nello scope selezionato.

### Output generati

In `output/ollama_benchmark`:

- `articles_by_model.json`: esiti, errori e fonti per modello ed esecuzione;
- `metrics_report.html`: success rate, latenze, conteggio fonti e grafici opzionali.

La latenza include avvio e arresto del backend. Il benchmark non legge dataset CSV e non misura throughput di token o qualità delle risposte. Domanda e numero di ripetizioni sono definiti dalle costanti `QUERY` e `REPEAT` nello script.

### Test dei contratti API (senza servizi attivi)

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

## Diagnostica sperimentale RAG

`python3 scripts/rag_diagnostic.py --help` elenca gli argomenti. Il data root predefinito è `lucene/prism-data`; per archivi scelti altrove occorre `--data-root`. Modello ed endpoint provengono da `PRISM_LLM_MODEL` e `PRISM_LLM_URL` (default `mistral-nemo:12b` e `http://localhost:11434/v1`), non dalle impostazioni persistite di PRISM.

`--force-json` prova `json_object`; `--simple-cite` prova fonti numeriche con un prompt alternativo. Nessuna modalità replica lo schema strict, il retrieval Lucene e l'intera politica del verificatore Java. I conteggi di successo sono riferiti all'esperimento selezionato, non alla qualità end-to-end dell'applicazione. Lo script usa il proprio client `urllib` e non eredita la validazione degli host di `LlmClient`.

`query_test.py` è conservato come riferimento della vecchia UI. Le dipendenze Selenium e webdriver-manager e l'eventuale download del driver riguardano quel tool legacy, non il runtime PRISM.
