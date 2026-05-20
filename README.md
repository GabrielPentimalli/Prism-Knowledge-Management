# Prism — Local-First Knowledge Manager

**Prism** è un knowledge manager **local-first** con chat RAG multi-agente: i tuoi documenti, gli indici, lo storico delle conversazioni e l'LLM stesso restano sempre sul tuo dispositivo. Nessun dato lascia la macchina, nessun servizio esterno è richiesto.

Costruito su **Apache Lucene** per il retrieval e su un **LLM locale** (Ollama o qualunque endpoint OpenAI-compatibile su `localhost`) per la sintesi, Prism trasforma una raccolta personale di PDF, articoli HTML, DOCX e note in una base di conoscenza interrogabile in linguaggio naturale — con citazioni verificabili a livello di paragrafo.

---

## Cosa rende Prism distintivo

### Privacy by design, non per opzione
- **`prism.local-mode.required=true`** non è un flag, è un'invariante: l'applicazione si rifiuta di operare se non punta a un endpoint LLM su `localhost`.
- Tutti i dati (documenti, indici Lucene, sessioni di chat, impostazioni) vivono in un *data root* scelto dall'utente (default `./prism-data`, override via onboarding).
- Un **`privacy.log`** append-only traccia ogni accesso allo storage locale: un audit trail leggibile, non un'opacità da black-box.

### Pipeline RAG a tre agenti
A differenza dei tipici flussi "retrieve + LLM" monolitici, Prism orchestra tre agenti dedicati:

```
User query → RetrievalAgent → SynthesisAgent → VerificationAgent → Answer + citations
                  │                  │                   │
             Lucene chunk        LLM locale        Filtra citazioni
             index (BM25)        (JSON output)     non ancorate ai chunk
```

1. **RetrievalAgent** — interroga l'indice Lucene dei chunk, bilanciando i risultati tra documenti quando lo scope è un intero *vault* (garantisce un numero minimo di chunk per documento, evita risultati monopolizzati da un singolo file).
2. **SynthesisAgent** — chiede all'LLM locale di rispondere usando **esclusivamente** i chunk forniti, in JSON strutturato con citazioni esplicite (`chunkId`, `docId`, `page`, `paragraphIndex`).
3. **VerificationAgent** — scarta ogni citazione il cui `chunkId` non corrisponde a uno dei chunk realmente recuperati: barriera anti-hallucination *deterministica*, non basata su prompt.

Se nessuna citazione sopravvive alla verifica, la risposta diventa `"non ho evidenza sufficiente nei documenti caricati"`. Mai inventato, mai approssimato.

### Citazioni ancorate al paragrafo
Ogni risposta espone le citazioni con coordinate precise (`docId` + pagina + indice di paragrafo + `chunkId`). L'interfaccia permette di saltare dal frammento citato al passaggio originale nel visualizzatore documenti.

### Vault: contesto modulare
I documenti si organizzano in **vault** (raccolte tematiche). La chat può essere scopata su:
- un **singolo documento** — domande puntuali, riferimenti precisi;
- un **intero vault** — domande comparative, sintesi cross-documento (con bilanciamento automatico dei chunk recuperati per evitare bias verso un singolo file).

### Ingestione multi-formato
Pipeline unica per cinque formati, con chunking sovrapposto configurabile:

| Formato | Estrazione | Granularità |
|---|---|---|
| PDF | Apache PDFBox | per pagina, poi paragrafi |
| HTML | Jsoup (h1–h6, p, li, blockquote, pre) | paragrafi semantici |
| DOCX | Apache POI | paragrafi `XWPFParagraph` |
| TXT / Markdown | parser nativo | blocchi separati da `\n\n` |

Chunk size e overlap sono parametrizzabili (default 900 / 150 caratteri).

---

## Architettura

```
┌─────────────────────────────────────────────────────────────┐
│  Web UI (Thymeleaf)                                         │
│  Home · Vault workspace · Document workspace · Viewer       │
│  Global search · Settings · Onboarding                      │
└───────────────────┬─────────────────────────────────────────┘
                    │  REST  (/api/vaults · /api/documents
                    │         /api/chat   · /api/search
                    │         /api/settings · /api/system)
┌───────────────────▼─────────────────────────────────────────┐
│  Application services                                       │
│  ├── ChatService  ── RetrievalAgent ─┐                      │
│  │                   SynthesisAgent ─┼─► LlmClient ──► LLM  │
│  │                   VerificationAgent┘    (HTTP localhost) │
│  ├── DocumentService                                        │
│  │      └── DocumentExtractionService                       │
│  │      └── DocumentChunkerService                          │
│  │      └── ChunkIndexService  ───────►  Lucene index       │
│  ├── VaultService                                           │
│  ├── SettingsService                                        │
│  └── SystemStatusService                                    │
└───────────────────┬─────────────────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────────────────┐
│  Local storage (./prism-data o data-root scelto)            │
│  ├── documents/        raw files + metadata                 │
│  ├── indices/chunks/   Lucene chunk index                   │
│  ├── vaults/           definizioni vault (JSON)             │
│  ├── chats/            sessioni di chat persistenti         │
│  └── logs/privacy.log  audit append-only                    │
└─────────────────────────────────────────────────────────────┘
```

---

## Stack tecnologico

| Componente | Tecnologia |
|---|---|
| Backend | Spring Boot 3.3.5 (Web + WebFlux + Validation) |
| Linguaggio | Java 21 |
| Motore di ricerca | Apache Lucene 10.3.1 (core, queryparser, analysis, highlighter) |
| Templating | Thymeleaf |
| LLM client | HTTP verso endpoint OpenAI-compatibile (Ollama by default) |
| Parsing PDF | Apache PDFBox 3.0.3 |
| Parsing DOCX | Apache POI 5.3.0 |
| Parsing HTML | Jsoup 1.21.2 |
| API docs | Springdoc OpenAPI / Swagger UI |
| Build | Maven |

---

## Prerequisiti

- **Java 21+**
- **Maven 3.8+**
- Un **LLM locale** raggiungibile via HTTP su `localhost`. Default: [Ollama](https://ollama.com) su `http://localhost:11434/v1` con modello `llama3.1:8b`.
  ```bash
  ollama pull llama3.1:8b
  ollama serve
  ```
- (Opzionale) **Python 3** per lo script di benchmark LLM.

---

## Avvio rapido

```bash
git clone https://github.com/GabrielPentimalli/Prism-Knowledge-Management.git
cd Prism-Knowledge-Management/lucene

mvn clean install
mvn spring-boot:run
```

Apri `http://localhost:8080`. Al primo avvio Prism mostra un **onboarding** che permette di:
1. scegliere la cartella per i dati locali (data root);
2. confermare l'endpoint LLM locale;
3. confermare la modalità local-first.

Da quel momento puoi creare vault, caricare documenti, e chattare. La documentazione REST è su `http://localhost:8080/swagger-ui.html`.

---

## Configurazione

Tutto via `lucene/src/main/resources/application.properties`. Le voci più rilevanti:

```properties
# LLM locale (deve essere su localhost: vincolo di sicurezza)
llm.base-url=http://localhost:11434/v1
llm.model=llama3.1:8b

# Storage local-first
prism.storage.default-data-root=./prism-data
prism.storage.bootstrap-file=${user.home}/.prism/settings.json
prism.local-mode.required=true
prism.onboarding.required=true
prism.minimum-free-disk-mb=512

# Indice e retrieval
prism.lucene.chunk-index-dir=indices/chunks
prism.chunk.size=900               # caratteri per chunk
prism.chunk.overlap=150            # overlap tra chunk consecutivi
prism.retrieval.top-k=8            # chunk recuperati per query
prism.retrieval.min-chunks-per-doc=2   # minimo garantito per documento in scope vault
prism.retrieval.vault-per-doc-cap=4    # tetto per documento in scope vault

# Upload
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=200MB
```

Le impostazioni runtime (data root, modello LLM) sono modificabili a caldo da `/settings` senza riavviare il backend.

---

## API REST (sintesi)

| Endpoint | Metodo | Funzione |
|---|---|---|
| `/api/vaults` | GET / POST / DELETE | Gestione vault |
| `/api/vaults/{id}` | GET | Dettaglio vault con documenti |
| `/api/documents` | POST (multipart) | Upload e indicizzazione documento |
| `/api/documents` | GET | Lista documenti |
| `/api/documents/{id}` | GET | Documento + chunk |
| `/api/documents/{id}/pages` | GET | Testo per pagina (visualizzatore) |
| `/api/chat` | POST | Invio messaggio con scope (DOCUMENT o VAULT) |
| `/api/chat/sessions` | GET | Sessioni per uno scope |
| `/api/chat/{sessionId}/history` | GET | Storico messaggi |
| `/api/search/global` | GET | Ricerca cross-documento con filtri (tipo, vault, intervallo date) |
| `/api/settings` | GET / PUT | Impostazioni Prism + ultimi log privacy |
| `/api/system/status` | GET | Stato runtime (LLM raggiungibile, spazio disco, ecc.) |

---

## Struttura del progetto

```
Prism-Knowledge-Management/
├── lucene/                        ← modulo applicativo Spring Boot
│   ├── pom.xml
│   └── src/main/
│       ├── java/it/uniroma3/sii/
│       │   ├── LuceneWebApp.java          ← entry point
│       │   ├── config/                    ← LuceneConfig, WebConfig
│       │   ├── controller/                ← REST + page controllers
│       │   ├── dto/                       ← request/response payloads
│       │   ├── model/                     ← Vault, KnowledgeDocument,
│       │   │                                DocumentChunk, Citation, …
│       │   ├── service/
│       │   │   ├── chat/                  ← Retrieval/Synthesis/Verification
│       │   │   │                            agents + LlmClient
│       │   │   ├── indexing/              ← extraction, chunking, Lucene index
│       │   │   └── storage/               ← repos locali + privacy log
│       │   └── utils/
│       └── resources/
│           ├── application.properties
│           ├── templates/                 ← Thymeleaf
│           └── static/css|js|images/
└── scripts/
    └── ollama_benchmark.py        ← confronto modelli LLM via /api/chat
```

---

## Benchmark LLM locali

Lo script `scripts/ollama_benchmark.py` rileva automaticamente i modelli installati in Ollama (`ollama list`), aggiorna `llm.model` in `application.properties`, e per ciascun modello invia una query fissa all'endpoint chat di Prism. Misura latenza, throughput, qualità della risposta.

```bash
# Backend Prism avviato su http://localhost:8080
python scripts/ollama_benchmark.py
```

Output in `output/ollama_benchmark/`:
- `raw_results.csv` — risultati per modello/run
- `summary_by_model.csv` — medie, p95 di latenza, throughput
- `summary.json` — riepilogo aggregato
- `charts/` — grafici (richiede `pip install matplotlib`)

---

## Licenza

Distribuito sotto licenza **MIT**. Vedi [`LICENSE`](LICENSE).
