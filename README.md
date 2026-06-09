# Prism

Prism is a local-first knowledge manager with a multi-agent RAG chat. Documents, indices, conversation history, and the language model all stay on your device: nothing is sent to an external service.

It uses Apache Lucene for retrieval and a local LLM (Ollama, or any OpenAI-compatible endpoint on `localhost`) for synthesis, turning a collection of PDF, HTML, DOCX, and text files into a knowledge base you can query in natural language, with citations anchored to the source paragraph.

## Key Features

- **Local by default.** All data (documents, Lucene indices, chat sessions, settings) lives in a user-chosen data root. The `prism.local-mode.required` invariant prevents the application from running against any LLM endpoint that is not on `localhost`, and an append-only `privacy.log` records every access to local storage.
- **Three-agent RAG pipeline.** Retrieval, synthesis, and verification are handled by separate agents, with a deterministic check that drops any citation not backed by a retrieved chunk (see below).
- **Paragraph-anchored citations.** Each answer cites its sources by `docId`, page, paragraph index, and `chunkId`, and the viewer links from a cited fragment back to the original passage.
- **Vaults.** Documents are grouped into thematic vaults. A chat can be scoped to a single document or to a whole vault; in vault scope, retrieved chunks are balanced across documents so no single file dominates.
- **Multi-format ingestion.** A single pipeline handles PDF, HTML, DOCX, TXT, and Markdown with configurable overlapping chunks.

## How the RAG Pipeline Works

```
User query -> RetrievalAgent -> SynthesisAgent -> VerificationAgent -> Answer + citations
                   |                  |                   |
              Lucene chunk        local LLM         drops citations
              index (BM25)        (JSON output)     not anchored to chunks
```

1. **RetrievalAgent** queries the Lucene chunk index. In vault scope it guarantees a minimum number of chunks per document, so the result set is not monopolized by one file.
2. **SynthesisAgent** asks the local LLM to answer using only the supplied chunks, returning structured JSON with explicit citations (`chunkId`, `docId`, `page`, `paragraphIndex`).
3. **VerificationAgent** discards any citation whose `chunkId` does not match a chunk that was actually retrieved. This is a deterministic filter, not a prompt-based safeguard.

If no citation survives verification, the response reports that there is insufficient evidence in the loaded documents rather than producing an unsupported answer.

## Ingestion Formats

| Format | Extraction | Granularity |
|---|---|---|
| PDF | Apache PDFBox | per page, then paragraphs |
| HTML | Jsoup (h1 to h6, p, li, blockquote, pre) | semantic paragraphs |
| DOCX | Apache POI | `XWPFParagraph` paragraphs |
| TXT / Markdown | native parser | blocks separated by `\n\n` |

Chunk size and overlap default to 900 and 150 characters and are configurable.

## Getting Started

### Prerequisites

- Java 21 or later, and Maven 3.8 or later.
- A local LLM reachable over HTTP on `localhost`. The default is [Ollama](https://ollama.com) on `http://localhost:11434/v1` with the `llama3.1:8b` model:
  ```bash
  ollama pull llama3.1:8b
  ollama serve
  ```
- Python 3 (optional), required only for the LLM benchmark script.

### Run

```bash
git clone https://github.com/GabrielPentimalli/Prism-Knowledge-Management.git
cd Prism-Knowledge-Management/lucene
mvn clean install
mvn spring-boot:run
```

Open `http://localhost:8080`. On first launch, an onboarding flow lets you choose the local data root, confirm the LLM endpoint, and confirm local-first mode. After that you can create vaults, upload documents, and chat. REST documentation is available at `http://localhost:8080/swagger-ui.html`.

## Configuration

Settings live in `lucene/src/main/resources/application.properties`. The data root and LLM model can also be changed at runtime from `/settings` without restarting the backend.

```properties
# Local LLM
llm.base-url=http://localhost:11434/v1
llm.model=llama3.1:8b

# Local-first storage
prism.storage.default-data-root=./prism-data
prism.storage.bootstrap-file=${user.home}/.prism/settings.json
prism.local-mode.required=true
prism.onboarding.required=true
prism.minimum-free-disk-mb=512

# Index and retrieval
prism.lucene.chunk-index-dir=indices/chunks
prism.chunk.size=900                    # characters per chunk
prism.chunk.overlap=150                 # overlap between consecutive chunks
prism.retrieval.top-k=8                 # chunks retrieved per query
prism.retrieval.min-chunks-per-doc=2    # minimum per document in vault scope
prism.retrieval.vault-per-doc-cap=4     # cap per document in vault scope

# Upload
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=200MB
```

## REST API

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/vaults` | GET / POST / DELETE | Vault management |
| `/api/vaults/{id}` | GET | Vault detail with documents |
| `/api/documents` | POST (multipart) | Upload and index a document |
| `/api/documents` | GET | List documents |
| `/api/documents/{id}` | GET | Document and chunks |
| `/api/documents/{id}/pages` | GET | Text by page (for the viewer) |
| `/api/chat` | POST | Send a message with a scope (DOCUMENT or VAULT) |
| `/api/chat/sessions` | GET | Sessions for a given scope |
| `/api/chat/{sessionId}/history` | GET | Message history |
| `/api/search/global` | GET | Cross-document search with filters (type, vault, date range) |
| `/api/settings` | GET / PUT | Settings and recent privacy log entries |
| `/api/system/status` | GET | Runtime status (LLM reachability, disk space) |

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
|       |   +-- utils/
|       +-- resources/
|           +-- application.properties
|           +-- templates/         # Thymeleaf
|           +-- static/css|js|images/
+-- scripts/
    +-- ollama_benchmark.py        # compares LLM models via /api/chat
```

## Benchmarking Local LLMs

`scripts/ollama_benchmark.py` detects the models installed in Ollama (`ollama list`), updates `llm.model` in `application.properties`, and sends a fixed query to the Prism chat endpoint for each model, measuring latency, throughput, and response quality.

```bash
# With the Prism backend running on http://localhost:8080
python scripts/ollama_benchmark.py
```

Results are written to `output/ollama_benchmark/`: `raw_results.csv` (per model and run), `summary_by_model.csv` (averages, latency p95, throughput), `summary.json` (aggregated summary), and `charts/` (requires `pip install matplotlib`).

## License

Distributed under the MIT License. See [`LICENSE`](LICENSE).
