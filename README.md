# PRISM — PubMed Retrieval and Indexing for Search and Matching

PRISM is a local search platform for PubMed Central content. It ingests article HTML, extracts structured information (articles, tables, images), builds Apache Lucene indexes, and exposes both a web search interface and a chat assistant backed by a local Ollama model.

## Key capabilities

- Multi-index search on **articles**, **tables**, and **images**
- Advanced Lucene query support (fielded queries, boolean operators, year ranges)
- Detail pages for each result with contextual content
- Natural-language chat that translates user intent into Lucene queries
- Built-in search quality metrics (precision, reciprocal rank, nDCG, latency)

## Architecture at a glance

1. **Data ingestion (Python)**  
   Downloads and preprocesses PubMed Central full-text HTML.
2. **Feature extraction (Python)**  
   Produces JSON datasets for tables and images with captions and context.
3. **Indexing + search (Java/Spring Boot + Lucene)**  
   Builds indexes and serves UI, APIs, and detail pages.
4. **Conversational layer (Spring Boot + Ollama)**  
   Converts natural language to Lucene syntax and runs the query.

## Repository structure

```text
PRISM/
├── lucene/                 # Spring Boot + Lucene application
├── scripts/                # Python ingestion/extraction/evaluation scripts
├── input/                  # Source data (HTML + extracted JSON/images)
├── output/                 # Benchmark and test outputs
└── README.md
```

## Requirements

- **Java 21**
- **Maven 3.9+**
- **Python 3.10+**
- **Ollama** (required for chat features)

Python dependencies used by scripts:

```bash
pip install biopython requests beautifulsoup4 tqdm nltk lxml selenium webdriver-manager
```

## Quick start

### 1. Run the backend

```bash
cd lucene
mvn spring-boot:run
```

Open:
- Search UI: `http://localhost:8080`
- Chat UI: `http://localhost:8080/chat`

By default (`lucene.index.initialize=true`), indexes are rebuilt at startup from data paths configured in `application.properties`.

### 2. Enable chat (Ollama)

```bash
ollama serve
ollama run llama3.1:8b
```

Default chat model is configured as:

```properties
llm.base-url=http://localhost:11434/v1
llm.model=llama3.1:8b
```

## Data pipeline (Python scripts)

### Download article HTML

Script: `scripts/pmc_scraper.py`

Before running, set:
- `Entrez.email` (required by NCBI)
- `QUERY`
- `MAX_ARTICLES`
- `OUT_DIR` (recommended: `input/pmc_html_articles`)

Then run:

```bash
python scripts/pmc_scraper.py
```

### Extract tables

```bash
python scripts/table_extraction.py input/pmc_html_articles input/tables
```

### Extract images

```bash
python scripts/image_extraction.py input/pmc_html_articles input/img
```

## Search guide

Supported fields by index:

| Index | Fields |
|---|---|
| `articoli` | `title`, `authors`, `articleAbstract`, `paragraphs`, `publicationYear` |
| `tabelle` | `caption`, `body`, `mentions`, `context_paragraphs` |
| `immagini` | `caption`, `alt`, `mentions`, `context_paragraphs`, `fileName` |

Examples:

```text
protein gene
title:cancer AND authors:Rossi
articleAbstract:"dietary fiber"
publicationYear:[2018 TO 2023]
caption:statistics OR body:"confidence interval"
```

## Chat API (main endpoints)

- `POST /api/chat` — send a message, receive answer + matched papers
- `GET /api/chat/sessions` — list chat sessions
- `POST /api/chat/sessions` — create a new session
- `GET /api/chat/{sessionId}/history` — retrieve session history
- `PATCH /api/chat/{sessionId}/name` — rename a session
- `DELETE /api/chat/{sessionId}` — delete a session

## Testing and benchmarking

Backend tests:

```bash
cd lucene
mvn test
```

Additional scripts:
- `scripts/query_test.py`: Selenium-based UI query regression
- `scripts/ollama_benchmark.py`: compare local Ollama models and export reports

## Authors

- [Gabriel Pentimalli](https://github.com/GabrielPentimalli)
- [Alessandro Peroni](https://github.com/smixale)
- [Tony Troy](https://github.com/troylion56)

## License

MIT License. See [LICENSE](LICENSE).
