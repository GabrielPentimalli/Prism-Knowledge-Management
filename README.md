# 🔍 Prism — Knowledge Management System

**Prism** è un sistema di gestione e ricerca della conoscenza che integra un motore di ricerca full-text basato su **Apache Lucene** con un backend **Spring Boot**, progettato per l'indicizzazione e il recupero di articoli biomedici da **PubMed**. Il sistema supporta ricerca su testo, immagini e tabelle, e include strumenti Python per benchmark e test delle query.

---

## 🚀 Tecnologie

| Componente | Tecnologia |
|---|---|
| Backend | Spring Boot 3.3.5 |
| Motore di ricerca | Apache Lucene 10.3.1 |
| Linguaggio | Java 21 |
| Parsing HTML | Jsoup 1.21.2 |
| Parsing PDF | Apache PDFBox 3.0.3 |
| API Docs | Springdoc OpenAPI / Swagger UI |
| Benchmark LLM | Python 3 + Ollama |
| Build | Maven |

---

## ⚙️ Prerequisiti

- **Java 21+**
- **Maven 3.8+**
- **Python 3** (solo per gli script)
- (Opzionale) **Ollama** in esecuzione su `http://localhost:11434` per il benchmark LLM
- (Opzionale) `matplotlib` per i grafici: `pip install matplotlib`

---

## 🔧 Build & Avvio

```bash
# Clona la repository
git clone https://github.com/GabrielPentimalli/Prism-Knowledge-Management.git
cd Prism-Knowledge-Management/lucene

# Build del progetto
mvn clean install

# Avvio dell'applicazione
mvn spring-boot:run
```

L'applicazione sarà disponibile su `http://localhost:8080`.  
La documentazione Swagger UI è accessibile su `http://localhost:8080/swagger-ui.html`.

---

## 🔎 Funzionalità

- **Indicizzazione full-text** di articoli PubMed (testo, immagini, tabelle) tramite Apache Lucene
- **Ricerca** con query parser avanzato e highlighting dei risultati
- **REST API** documentata con Swagger/OpenAPI
- **Interfaccia web** servita tramite Thymeleaf
- **Parsing** di documenti HTML e PDF
- **Ricerca reattiva** tramite Spring WebFlux

---

## 🧪 Script Python

### Query di Test

```bash
python scripts/query_test.py
```

Esegue una suite di test per misurare l'efficienza del sistema di ricerca.

### Benchmark LLM (Ollama)

Confronta più modelli Ollama locali sugli stessi prompt.

```bash
python scripts/ollama_benchmark.py \
  --models llama3.1:8b,mistral:7b,phi3:mini \
  --input scripts/input/ollama_benchmark_prompts.csv \
  --output-dir output/ollama_benchmark \
  --temperature 0 \
  --num-predict 256 \
  --repeat 2
```

**Output generati** (in `output/ollama_benchmark/`):
- `raw_results.csv` — risultati per prompt/modello/run
- `summary_by_model.csv` — medie, p95 latenza, throughput
- `summary_by_category.csv` — confronto per categoria
- `pairwise_win_rate.csv` — win-rate tra coppie di modelli
- `summary.json` — riepilogo completo
- `charts/` — grafici PNG (latency boxplot, throughput, quality heatmap)

**Formato CSV di input** (colonna `prompt` obbligatoria):

| Campo | Tipo |
|---|---|
| `id` | opzionale |
| `category` | opzionale |
| `prompt` | **obbligatoria** |
| `reference` | opzionale (abilita metriche qualità) |

---

## 📁 Struttura degli Indici

Gli indici Lucene sono separati per tipo di contenuto:

- `lucene/index/` — testo degli articoli
- `lucene/index_img/` — metadati delle immagini
- `lucene/index_tables/` — contenuto delle tabelle

I dati sorgente (pagine HTML di PubMed) vanno collocati in `lucene/prism-data/`.

---

## 📜 Licenza

Questo progetto è distribuito sotto licenza **MIT**. Vedi il file [`LICENSE`](LICENSE) per i dettagli.
