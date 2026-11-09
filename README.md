# Prism Knowledge Management

Prism is a local-first knowledge manager with a multi-agent RAG chat. It stores documents, indices, conversation history, and settings on the local filesystem, and its LLM client accepts only HTTP/HTTPS endpoints on `localhost`, `127.0.0.1`, or `[::1]`.

It uses Apache Lucene for retrieval and a local LLM (Ollama, or a compatible endpoint) for synthesis, turning PDF, HTML, DOCX, Markdown, and text files into a knowledge base you can query in natural language. Citations carry chunk, page, and paragraph metadata; the viewer opens the cited page.

## Key Features

- **Local storage and inference.** Documents, indices, chats, settings, and logs live in a user-chosen data root. A bootstrap settings copy is stored separately at `${user.home}/.prism/settings.json` by default. `LlmClient` enforces the local endpoint restriction directly; `privacy.log` records selected application operations, not every filesystem access.
- **Three-agent RAG pipeline.** Retrieval, synthesis, and verification are handled by separate agents, with a deterministic check that drops any citation not backed by a retrieved chunk (see below).
- **Source references.** When present, citations include `docId`, page, paragraph index, and `chunkId`. Links open the source page; the viewer does not highlight the exact cited paragraph. Answers can be returned without citations.
- **Vaults.** Documents are grouped into thematic vaults. A chat can be scoped to a single document or to a whole vault; vault retrieval applies per-document caps and minimum sampling when enough chunks are available.
- **Multi-format ingestion.** A single pipeline handles PDF, HTML, DOCX, TXT, and Markdown with configurable overlapping chunks.

## How the RAG Pipeline Works

```
User query -> RetrievalAgent -> SynthesisAgent -> VerificationAgent -> Answer + citations
                   |                  |                   |
              Lucene chunk        local LLM         drops citations
              index (BM25)        (JSON output)     not anchored to chunks
```

1. **RetrievalAgent** queries the Lucene chunk index. In vault scope it caps the chunks per document and samples additional chunks for documents below the configured minimum, when enough chunks are available. Sampling improves coverage but does not guarantee relevance.
2. **SynthesisAgent** asks the local LLM to answer using the supplied chunks, with structured JSON containing `answer` and `sources` (1-based fragment numbers). The backend maps valid numbers to citations with real document and chunk metadata. The parser also tolerates some alternative field names and prose output.
3. **VerificationAgent** discards citations whose `chunkId` was not retrieved, restores metadata from the retrieved chunks, and deduplicates references to the same document and page. This deterministic filter checks reference integrity, not whether every claim is supported by the cited text.

The application preserves a non-empty answer even if no citation survives verification. No retrieved chunks, an empty or unusable answer, or an explicit insufficient-evidence response produce the fallback “non ho evidenza sufficiente nei documenti caricati”. The absence of citations alone does not trigger that fallback.

For follow-up questions, `QueryRewriteService` can first ask the LLM to rewrite the question using recent history; retrieval also includes recent messages. A rewrite error falls back to the original question.

## Ingestion Formats

| Format | Text used for indexing | Viewer text |
|---|---|---|
| PDF | PDFBox per page; text blocks shorter than 30 characters are skipped | All extracted page text |
| HTML | Jsoup `p` elements of at least 20 characters; fallback to body text if none qualify | `h1`–`h6`, `p`, `li`, `blockquote`, `pre`; fallback to body text |
| DOCX | Top-level `XWPFParagraph` paragraphs of at least 20 characters | Non-empty top-level paragraphs |
| TXT / Markdown | UTF-8 text split on newlines or blank lines; blocks shorter than 20 characters are skipped | Complete text |

Additional text extensions include `.text`, `.log`, `.csv`, `.tsv`, `.json`, `.xml`, `.yaml`, and `.yml`; these are indexed as plain text. There is no OCR or image indexing. Only PDFs retain physical page numbers; other formats use page 1.

Chunk size and overlap default to 900 and 150 characters and are configurable targets, not strict limits: the chunker keeps whole paragraphs. An unchanged file reuses its existing document only if indexing completed; unfinished or failed indexing is retried with the same document ID.

## Getting Started

### Prerequisites

- JDK 21 and Maven 3.8 or later. The project targets Java 21; this does not imply compatibility with every newer compiler. The available JDK 27 fails during Lombok annotation processing.
- A local LLM reachable over HTTP/HTTPS on an allowed loopback host. The configured default is [Ollama](https://ollama.com) on `http://localhost:11434/v1` with `mistral-nemo:12b`:
  ```bash
  ollama pull mistral-nemo:12b
  ollama serve
  ```
- Python 3.9 or later (optional, for support scripts). Node.js with the built-in test runner is optional for the JavaScript regression tests.

### Run

```bash
git clone https://github.com/GabrielPentimalli/Prism-Knowledge-Management.git
cd Prism-Knowledge-Management/lucene
mvn clean install
mvn spring-boot:run
```

Open `http://localhost:8080`. On first launch, onboarding lets you choose the data root, LLM model, and endpoint, prefilled from the current settings. Local mode is enforced by the client rather than selected by a checkbox. After that you can create vaults, upload documents, and chat. Springdoc provides Swagger UI at `/swagger-ui.html`; this route has not been verified in a running instance during the local audit.

## Configuration

`lucene/src/main/resources/application.properties` defines defaults. Persisted settings take precedence: the bootstrap file identifies the data root, then `<data-root>/config/settings.json` is loaded when present. The data root, model, and endpoint can be changed at runtime from `/settings`; the LLM client is reconfigured without restarting the backend.

Changing the data root selects a different archive for subsequent operations; it does not migrate existing documents, indices, or chats. LLM configuration is validated before settings are saved. JSON files are written through temporary files and replacement, using an atomic move when supported; there is no transaction spanning JSON files and the Lucene index.

```properties
# Local LLM
llm.base-url=http://localhost:11434/v1
llm.model=mistral-nemo:12b

# Local-first storage
prism.storage.default-data-root=./prism-data
prism.storage.bootstrap-file=${user.home}/.prism/settings.json
# Legacy flags: declared but not read by Java code
prism.local-mode.required=true
prism.onboarding.required=true
prism.minimum-free-disk-mb=512

# Index and retrieval
prism.lucene.chunk-index-dir=indices/chunks
# Approximate targets in characters, preserving whole paragraphs
prism.chunk.size=900
prism.chunk.overlap=150
prism.retrieval.top-k=8
prism.retrieval.min-chunks-per-doc=2
prism.retrieval.vault-per-doc-cap=4

# Upload
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=200MB
```

In document scope, `top-k` limits text matches; sampling is used if there are none. In vault scope, the initial request is `max(top-k, documentCount × vault-per-doc-cap)`, followed by per-document capping and minimum sampling. The effective cap is at least the configured minimum (itself at least 1). There is no global token budget. The disk threshold is reported by `/api/system/status`; it does not block uploads.

## REST API

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/onboarding/init` | POST | Initialize settings with `dataRoot`, `llmModel`, `llmBaseUrl` |
| `/api/vaults` | GET / POST | List vaults / create with `name` |
| `/api/vaults/{id}` | GET / DELETE | Vault detail / delete the vault, keeping documents |
| `/api/vaults/{id}/documents` | POST (multipart) | Upload using `files` (one or more) |
| `/api/vaults/{id}/documents/{docId}` | DELETE | Remove an association, keeping the document |
| `/api/documents` | POST (multipart) | Upload and index using `file` |
| `/api/documents` | GET | List documents |
| `/api/documents/{id}` | GET | Document and chunks |
| `/api/documents/{id}/pages` | GET | Text by page (for the viewer) |
| `/api/documents/{id}/source` | GET | Original source inline; HTML/XML sandboxed by CSP |
| `/api/chat` | POST | Send a message with a scope (DOCUMENT or VAULT) |
| `/api/chat/sessions` | GET | Requires `scopeType` and `scopeId` |
| `/api/chat/{sessionId}/history` | GET | Message history |
| `/api/search/global` | GET | Requires `q`; optional `fileType`, `vaultId`, `fileName`, `minSizeKb`, `maxSizeKb` |
| `/api/settings` | GET / PUT | Settings and recent privacy log entries |
| `/api/system/status` | GET | Runtime status (LLM reachability, disk space) |
| `/api/data/delete` | POST | Delete data selectively or with `mode: "TOTAL"` |

Example chat request for an existing document (`sessionId` is optional):

```json
{"scopeType":"DOCUMENT","scopeId":"<document-id>","message":"Quali sono i punti principali?"}
```

The response contains `sessionId`, `answer`, `citations`, and `success`. `success: true` means the pipeline completed, including an insufficient-evidence answer; it is not a correctness score. Uploads can return HTTP 200 with per-file `status: "ERROR"`; inspect the file status. Search returns at most 50 chunk hits, with `total` equal to the number returned; date filters and backend pagination are not implemented.

Selective deletion accepts `documentId`, `vaultId`, or `scopeType` with `scopeId`. Deleting a document this way also removes its index entries, vault associations, and document-scoped chats. Deleting a vault through `/api/vaults/{id}` keeps its chats; selective vault deletion through `/api/data/delete` also deletes them. `TOTAL` removes documents, vaults, indices, and chats, but preserves configuration, logs, onboarding state, and Ollama models. There is no direct `DELETE /api/documents/{id}` endpoint.

## Privacy and operational limits

The LLM host restriction is enforced independently of the legacy configuration flags. The backend has no authentication or explicit loopback-only binding, and onboarding redirects the entry page without blocking all APIs. Deploy it according to those limits. Local storage and inference do not amount to complete network isolation or encryption at rest.

HTML/XML source responses use CSP sandboxing to restrict active content and external resources; exact browser isolation has not been verified end-to-end. LLM calls are synchronous and have no explicit application timeout or retry policy. DEBUG chat logs can contain questions and rewrites.

## Verification

From `lucene/`, use `mvn verify` with JDK 21 for compilation, Java tests, and packaging. From the repository root:

```bash
node --test lucene/src/test/js/settings.test.js
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

See [support scripts](scripts/README.md) for experimental and legacy tool limits.

## Project Structure

```
Prism-Knowledge-Management/
+-- lucene/                        # Spring Boot application module
|   +-- pom.xml
|   +-- src/main/
|       +-- java/it/uniroma3/sii/
|       |   +-- LuceneWebApp.java  # entry point
|       |   +-- config/            # LuceneConfig, WebConfig
|       |   +-- controller/        # REST and page controllers
|       |   +-- dto/               # request and response payloads
|       |   +-- model/             # Vault, KnowledgeDocument, DocumentChunk, ...
|       |   +-- service/
|       |   |   +-- chat/          # Retrieval/Synthesis/Verification, LlmClient
|       |   |   +-- indexing/      # extraction, chunking, Lucene index
|       |   |   +-- storage/       # local repositories and privacy log
|       +-- resources/
|           +-- application.properties
|           +-- templates/         # Thymeleaf
|           +-- static/css|js|images/
+-- scripts/
    +-- ollama_benchmark.py        # compares LLM models via /api/chat
```

## Benchmarking Local LLMs

`scripts/ollama_benchmark.py` detects the models installed in Ollama (`ollama list`) and sends a fixed query for each model to an existing document or vault. It starts and stops Prism for each run, selects the model through the settings API, and restores both the persisted model and `application.properties` afterward. Reported latency includes backend startup and shutdown.

```bash
# Stop Prism first; use the ID of an already loaded document or vault
python scripts/ollama_benchmark.py --scope-type DOCUMENT --scope-id <document-id>
```

Results are written to `output/ollama_benchmark/`: `articles_by_model.json` (sources and results per run) and `metrics_report.html` (success rate, latency, source counts, and optional charts if `matplotlib` is installed). The script does not measure token throughput or answer quality.

## License

Distributed under the MIT License. See [`LICENSE`](LICENSE).
