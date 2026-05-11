#  Script di Processing
Questa cartella contiene il core tecnologico per la gestione dei dati, suddiviso in moduli per il recupero, l'estrazione e l'analisi delle performance.
## Download HTML
Script progettato per l'acquisizione automatizzata delle pagine web.
## Estrazione Dati 
Moduli dedicati all'elaborazione dei file HTML scaricati per isolare informazioni sulle tabelle e sulle immagini.
## Query di Test
Suite di test per misurare l'efficienza del database e del sistema.

## Benchmark comparativo Ollama
Script: `scripts/ollama_benchmark.py`

Permette di confrontare formalmente più modelli locali Ollama sugli stessi prompt, producendo:
- risultati grezzi per singola chiamata;
- riepiloghi per modello e categoria;
- confronto pairwise (win-rate);
- grafici pronti per presentazioni (se `matplotlib` è installato).

### Prerequisiti
- Ollama in esecuzione (`http://localhost:11434`)
- Modelli già scaricati (`ollama list`)
- Python 3
- (Opzionale) `matplotlib` per i grafici:
  - `pip install matplotlib`

### Dataset input
CSV con almeno la colonna `prompt`.
Colonne supportate:
- `id` (opzionale)
- `category` (opzionale)
- `prompt` (obbligatoria)
- `reference` (opzionale, abilita metriche qualità)

Esempio pronto:
`scripts/input/ollama_benchmark_prompts.csv`

### Esecuzione
```bash
python scripts/ollama_benchmark.py \
  --models llama3.1:8b,mistral:7b,phi3:mini \
  --input scripts/input/ollama_benchmark_prompts.csv \
  --output-dir output/ollama_benchmark \
  --temperature 0 \
  --num-predict 256 \
  --repeat 2
```

### Output generati
Nella cartella `output/ollama_benchmark`:
- `raw_results.csv`: una riga per prompt/modello/run con latenza, token/s, output ed errori.
- `summary_by_model.csv`: medie, p95 latenza, throughput, qualità media.
- `summary_by_category.csv`: confronto per categoria.
- `pairwise_win_rate.csv`: vittorie/sconfitte tra coppie di modelli su qualità.
- `summary.json`: riepilogo completo.
- `charts/*.png`:
  - `latency_boxplot.png`
  - `throughput_bar.png`
  - `quality_grouped_bar.png` (se presente `reference`)
  - `quality_heatmap_by_category.png` (se presente `reference`)
