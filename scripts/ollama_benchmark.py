#!/usr/bin/env python3
"""
Benchmark automatico del chatbot PRISM variando il modello LLM in application.properties.

Lo script:
- legge automaticamente i modelli da `ollama list`
- per ogni modello aggiorna `llm.model` in `lucene/src/main/resources/application.properties`
- invia una singola query fissa all'endpoint chatbot del progetto
- salva metriche e riepiloghi in `output/ollama_benchmark`

Prerequisito: backend PRISM avviato su http://localhost:8080.
"""

from __future__ import annotations

import base64
import html
import json
import math
import re
import socket
import statistics
import subprocess
import time
from collections import defaultdict
from io import BytesIO
from pathlib import Path
from typing import Any
from urllib import error as urlerror
from urllib import request as urlrequest

PROJECT_ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = PROJECT_ROOT / "output" / "ollama_benchmark"
APP_PROPS_PATH = PROJECT_ROOT / "lucene" / "src" / "main" / "resources" / "application.properties"
CHAT_API_URL = "http://localhost:8080/api/chat"
CHAT_SESSIONS_URL = "http://localhost:8080/api/chat/sessions"
SERVER_HOST = "127.0.0.1"
SERVER_PORT = 8080
QUERY = "articoli pubblicati nel 2025 che parlano di cancro e malattie cardiovascolari"
REQUEST_TIMEOUT_S = 180
REPEAT = 1
BACKEND_READY_TIMEOUT_S = 180
LUCENE_DIR = PROJECT_ROOT / "lucene"
ARTICLES_FILE = OUTPUT_DIR / "articles_by_model.json"
METRICS_FILE = OUTPUT_DIR / "metrics_report.html"


def get_models_from_ollama_list() -> list[str]:
    try:
        res = subprocess.run(
            ["ollama", "list"],
            check=True,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError as e:
        raise RuntimeError("Comando 'ollama' non trovato nel PATH.") from e
    except subprocess.CalledProcessError as e:
        msg = (e.stderr or e.stdout or str(e)).strip()
        raise RuntimeError(f"Errore eseguendo 'ollama list': {msg}") from e

    lines = [ln.strip() for ln in res.stdout.splitlines() if ln.strip()]
    if len(lines) <= 1:
        raise RuntimeError("Nessun modello disponibile in 'ollama list'.")

    models: list[str] = []
    for line in lines[1:]:
        parts = line.split()
        if parts:
            models.append(parts[0])

    models = sorted(set(models))
    if not models:
        raise RuntimeError("Impossibile estrarre i nomi modello da 'ollama list'.")
    if len(models) < 2:
        raise ValueError("Servono almeno 2 modelli locali per un confronto.")
    return models


def read_text(path: Path) -> str:
    if not path.exists():
        raise FileNotFoundError(f"File non trovato: {path}")
    return path.read_text(encoding="utf-8")


def extract_llm_model(props_text: str) -> str:
    match = re.search(r"(?m)^llm\.model=(.+)$", props_text)
    if not match:
        raise RuntimeError("Chiave 'llm.model' non trovata in application.properties.")
    return match.group(1).strip()


def set_llm_model(props_text: str, model: str) -> str:
    if not re.search(r"(?m)^llm\.model=", props_text):
        raise RuntimeError("Chiave 'llm.model' non trovata in application.properties.")
    return re.sub(r"(?m)^llm\.model=.*$", f"llm.model={model}", props_text, count=1)


def write_text(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8")


def call_chatbot(query: str, timeout_s: int) -> dict[str, Any]:
    payload = {
        "sessionId": "",
        "message": query,
    }
    req = urlrequest.Request(
        url=CHAT_API_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urlrequest.urlopen(req, timeout=timeout_s) as resp:
        body = resp.read().decode("utf-8")
        data = json.loads(body)
        if not isinstance(data, dict):
            raise RuntimeError("Risposta JSON non valida da /api/chat.")
        return data


def is_port_open(host: str, port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(0.5)
        return sock.connect_ex((host, port)) == 0


def wait_backend_ready(timeout_s: int) -> None:
    deadline = time.time() + timeout_s
    last_error = None
    while time.time() < deadline:
        try:
            req = urlrequest.Request(url=CHAT_SESSIONS_URL, method="GET")
            with urlrequest.urlopen(req, timeout=3) as resp:
                if 200 <= resp.status < 300:
                    return
        except Exception as e:
            last_error = e
        time.sleep(1.0)
    raise RuntimeError(f"Backend non pronto entro {timeout_s}s. Ultimo errore: {last_error}")


def start_backend() -> subprocess.Popen[str]:
    process = subprocess.Popen(
        ["mvn", "-q", "spring-boot:run"],
        cwd=LUCENE_DIR,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        text=True,
    )
    try:
        wait_backend_ready(BACKEND_READY_TIMEOUT_S)
    except Exception:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)
        raise
    return process


def stop_backend(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=30)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)


def ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def reset_output_files(paths: list[Path]) -> None:
    for path in paths:
        ensure_parent(path)
        if path.exists():
            path.unlink()


def write_json(path: Path, data: Any) -> None:
    ensure_parent(path)
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


def percentile(values: list[float], q: float) -> float | None:
    if not values:
        return None
    if len(values) == 1:
        return values[0]
    if not 0 <= q <= 100:
        raise ValueError("q deve essere tra 0 e 100")
    k = (len(values) - 1) * (q / 100.0)
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return values[int(k)]
    d0 = values[f] * (c - k)
    d1 = values[c] * (k - f)
    return d0 + d1


def to_float(value: Any) -> float | None:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value))
    except (TypeError, ValueError):
        return None


def normalize_article(item: Any, fallback_rank: int) -> dict[str, Any]:
    if isinstance(item, str):
        return {"rank": fallback_rank, "title": item.strip()}

    if not isinstance(item, dict):
        return {"rank": fallback_rank, "title": str(item).strip()}

    title = (
        item.get("title")
        or item.get("name")
        or item.get("articleTitle")
        or item.get("documentTitle")
        or item.get("label")
        or ""
    )
    article_id = item.get("id") or item.get("pmid") or item.get("pmcid") or item.get("doi")
    url = item.get("url") or item.get("link")
    snippet = item.get("abstract") or item.get("summary") or item.get("text")
    normalized = {"rank": fallback_rank, "title": str(title).strip()}
    if article_id is not None:
        normalized["id"] = str(article_id).strip()
    if url is not None:
        normalized["url"] = str(url).strip()
    if snippet is not None:
        normalized["snippet"] = str(snippet).strip()
    return normalized


def parse_articles_from_bot_message(bot_message: str) -> list[dict[str, Any]]:
    articles: list[dict[str, Any]] = []
    if not bot_message.strip():
        return articles
    for line in bot_message.splitlines():
        cleaned = re.sub(r"^\s*(?:[-*]|\d+[.)])\s*", "", line).strip()
        if not cleaned:
            continue
        if len(cleaned) < 12:
            continue
        articles.append({"rank": len(articles) + 1, "title": cleaned})
    return articles


def extract_articles(response_data: dict[str, Any], bot_message: str) -> list[dict[str, Any]]:
    containers: list[Any] = [response_data]
    for key in ("data", "payload", "result"):
        nested = response_data.get(key)
        if nested is not None:
            containers.append(nested)

    raw_articles: list[Any] = []
    article_keys = ("articles", "results", "documents", "items", "sources", "citations")
    for container in containers:
        if isinstance(container, dict):
            for key in article_keys:
                candidate = container.get(key)
                if isinstance(candidate, list):
                    raw_articles.extend(candidate)

    normalized = [
        normalize_article(item, fallback_rank=i + 1)
        for i, item in enumerate(raw_articles)
        if str(item).strip()
    ]
    if normalized:
        return normalized
    return parse_articles_from_bot_message(bot_message)


def render_chart_data_uri(
    labels: list[str], values: list[float], title: str, y_label: str
) -> str | None:
    try:
        import matplotlib.pyplot as plt
    except ImportError:
        return None

    fig, ax = plt.subplots(figsize=(10, 5))
    ax.bar(labels, values, color="#3f6df6")
    ax.set_title(title)
    ax.set_ylabel(y_label)
    ax.grid(axis="y", linestyle="--", alpha=0.35)
    fig.tight_layout()
    buffer = BytesIO()
    fig.savefig(buffer, format="png", dpi=130)
    plt.close(fig)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    return f"data:image/png;base64,{encoded}"


def build_charts(per_model: list[dict[str, Any]]) -> dict[str, str]:
    labels = [str(x["model"]) for x in per_model]
    success_vals = [float(x["success_rate"]) * 100.0 for x in per_model]
    latency_vals = [to_float(x["latency_ms_mean"]) or 0.0 for x in per_model]
    article_vals = [to_float(x["articles_found_mean"]) or 0.0 for x in per_model]

    charts: dict[str, str] = {}
    success_chart = render_chart_data_uri(
        labels, success_vals, "Success rate per modello", "Success rate (%)"
    )
    if success_chart:
        charts["Success rate (%)"] = success_chart

    latency_chart = render_chart_data_uri(
        labels, latency_vals, "Latenza media per modello", "Latenza media (ms)"
    )
    if latency_chart:
        charts["Latenza media (ms)"] = latency_chart

    article_chart = render_chart_data_uri(
        labels, article_vals, "Articoli trovati (media) per modello", "Articoli (media)"
    )
    if article_chart:
        charts["Articoli trovati (media)"] = article_chart

    return charts


def write_metrics_html(
    path: Path,
    query: str,
    elapsed_total_s: float,
    total_calls: int,
    successful_calls: int,
    success_rate: float | None,
    restored_model: str,
    per_model: list[dict[str, Any]],
    charts: dict[str, str],
) -> None:
    ensure_parent(path)
    rows_html = []
    for row in per_model:
        rows_html.append(
            "<tr>"
            f"<td>{html.escape(str(row['model']))}</td>"
            f"<td>{row['samples']}</td>"
            f"<td>{row['success_rate']:.2%}</td>"
            f"<td>{row['latency_ms_mean'] if row['latency_ms_mean'] is not None else 'n/a'}</td>"
            f"<td>{row['latency_ms_median'] if row['latency_ms_median'] is not None else 'n/a'}</td>"
            f"<td>{row['latency_ms_p95'] if row['latency_ms_p95'] is not None else 'n/a'}</td>"
            f"<td>{row['articles_found_mean'] if row['articles_found_mean'] is not None else 'n/a'}</td>"
            "</tr>"
        )

    charts_html = []
    if charts:
        for metric_name, data_uri in charts.items():
            charts_html.append(
                "<section>"
                f"<h3>{html.escape(metric_name)}</h3>"
                f'<img src="{data_uri}" alt="{html.escape(metric_name)}" />'
                "</section>"
            )
    else:
        charts_html.append(
            "<p>Grafici non generati: installa matplotlib (`pip install matplotlib`).</p>"
        )

    page = f"""<!doctype html>
<html lang="it">
<head>
  <meta charset="utf-8" />
  <title>PRISM - Benchmark modelli</title>
  <style>
    body {{ font-family: Arial, sans-serif; margin: 24px; color: #1f2937; }}
    table {{ border-collapse: collapse; width: 100%; margin: 16px 0 24px; }}
    th, td {{ border: 1px solid #d1d5db; padding: 8px; text-align: left; }}
    th {{ background: #f3f4f6; }}
    h1, h2, h3 {{ margin-bottom: 8px; }}
    img {{ max-width: 100%; border: 1px solid #e5e7eb; margin: 6px 0 16px; }}
  </style>
</head>
<body>
  <h1>Benchmark modelli Ollama</h1>
  <p><strong>Query:</strong> {html.escape(query)}</p>
  <p><strong>Modello ripristinato:</strong> {html.escape(restored_model)}</p>
  <p><strong>Chiamate totali:</strong> {total_calls}</p>
  <p><strong>Chiamate riuscite:</strong> {successful_calls}</p>
  <p><strong>Success rate globale:</strong> {f"{success_rate:.2%}" if success_rate is not None else "n/a"}</p>
  <p><strong>Tempo totale:</strong> {elapsed_total_s:.2f}s</p>

  <h2>Metriche per modello</h2>
  <table>
    <thead>
      <tr>
        <th>Modello</th>
        <th>Campioni</th>
        <th>Success rate</th>
        <th>Latenza media (ms)</th>
        <th>Latenza mediana (ms)</th>
        <th>Latenza p95 (ms)</th>
        <th>Articoli trovati (media)</th>
      </tr>
    </thead>
    <tbody>
      {''.join(rows_html)}
    </tbody>
  </table>

  <h2>Visualizzazioni comparative</h2>
  {''.join(charts_html)}
</body>
</html>
"""
    path.write_text(page, encoding="utf-8")


def main() -> int:
    models = get_models_from_ollama_list()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    reset_output_files([ARTICLES_FILE, METRICS_FILE])

    if is_port_open(SERVER_HOST, SERVER_PORT):
        raise RuntimeError(
            f"La porta {SERVER_PORT} è già in uso. Arresta eventuali istanze PRISM e rilancia lo script."
        )

    original_props = read_text(APP_PROPS_PATH)
    original_model = extract_llm_model(original_props)
    print(f"Modello iniziale da ripristinare: {original_model}")
    print(f"Modelli da valutare: {', '.join(models)}")
    print(f"Query: {QUERY}")

    rows: list[dict[str, Any]] = []
    articles_by_model_runs: dict[str, list[dict[str, Any]]] = defaultdict(list)
    start_all = time.perf_counter()
    total_calls = len(models) * REPEAT
    call_idx = 0

    try:
        for model in models:
            for rep in range(1, REPEAT + 1):
                call_idx += 1
                print(f"[{call_idx}/{total_calls}] modello={model} run={rep}")

                updated_props = set_llm_model(read_text(APP_PROPS_PATH), model)
                write_text(APP_PROPS_PATH, updated_props)

                t0 = time.perf_counter()
                err_msg = None
                bot_message = ""
                success = False
                total_results = None
                session_id = None
                raw_response = None
                articles_found: list[dict[str, Any]] = []
                backend_process = None
                try:
                    backend_process = start_backend()
                    response_data = call_chatbot(QUERY, REQUEST_TIMEOUT_S)
                    raw_response = json.dumps(response_data, ensure_ascii=False)
                    bot_message = str(response_data.get("botMessage") or "").strip()
                    success = bool(response_data.get("success", False))
                    total_results = response_data.get("totalResults")
                    session_id = response_data.get("sessionId")
                    articles_found = extract_articles(response_data, bot_message)
                except urlerror.HTTPError as e:
                    err_msg = f"HTTPError {e.code}: {e.reason}"
                except urlerror.URLError as e:
                    err_msg = f"URLError: {e.reason}"
                except TimeoutError:
                    err_msg = "TimeoutError"
                except Exception as e:
                    err_msg = f"{type(e).__name__}: {e}"
                finally:
                    if backend_process is not None:
                        stop_backend(backend_process)

                elapsed_ms = round((time.perf_counter() - t0) * 1000.0, 2)
                articles_count = len(articles_found)
                if isinstance(total_results, int):
                    articles_count = total_results
                rows.append(
                    {
                        "model": model,
                        "repeat": rep,
                        "query": QUERY,
                        "success": success and err_msg is None,
                        "error": err_msg,
                        "latency_ms": elapsed_ms,
                        "total_results": total_results,
                        "articles_count": articles_count,
                        "session_id": session_id,
                        "bot_message": bot_message,
                        "raw_response": raw_response,
                    }
                )
                articles_by_model_runs[model].append(
                    {
                        "run": rep,
                        "success": success and err_msg is None,
                        "error": err_msg,
                        "latency_ms": elapsed_ms,
                        "articles_count": articles_count,
                        "articles": articles_found,
                    }
                )
    finally:
        write_text(APP_PROPS_PATH, original_props)
        print(f"Ripristinato llm.model={original_model} in application.properties")

    elapsed_total = round(time.perf_counter() - start_all, 2)
    successful = [r for r in rows if r["success"]]

    per_model: list[dict[str, Any]] = []
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for r in rows:
        grouped[r["model"]].append(r)

    for model, data in sorted(grouped.items()):
        latency_vals = sorted(float(x["latency_ms"]) for x in data if x["success"])
        articles_vals = [int(x["articles_count"]) for x in data if x["success"] and isinstance(x["articles_count"], int)]
        per_model.append(
            {
                "model": model,
                "samples": len(data),
                "success_rate": round(sum(1 for x in data if x["success"]) / len(data), 4),
                "latency_ms_mean": round(statistics.fmean(latency_vals), 2) if latency_vals else None,
                "latency_ms_median": round(statistics.median(latency_vals), 2) if latency_vals else None,
                "latency_ms_p95": round(percentile(latency_vals, 95) or 0.0, 2) if latency_vals else None,
                "articles_found_mean": round(statistics.fmean(articles_vals), 2) if articles_vals else None,
            }
        )

    per_model_articles: list[dict[str, Any]] = []
    for model in sorted(articles_by_model_runs):
        all_articles: list[dict[str, Any]] = []
        seen_keys: set[tuple[str, str]] = set()
        for run in articles_by_model_runs[model]:
            for article in run["articles"]:
                title_key = str(article.get("title") or "").strip().lower()
                id_key = str(article.get("id") or "").strip().lower()
                unique_key = (id_key, title_key)
                if unique_key in seen_keys:
                    continue
                seen_keys.add(unique_key)
                all_articles.append(article)
        per_model_articles.append(
            {
                "model": model,
                "runs": articles_by_model_runs[model],
                "unique_articles_count": len(all_articles),
                "unique_articles": all_articles,
            }
        )

    charts = build_charts(per_model)
    success_rate_global = round(len(successful) / len(rows), 4) if rows else None
    write_metrics_html(
        METRICS_FILE,
        query=QUERY,
        elapsed_total_s=elapsed_total,
        total_calls=len(rows),
        successful_calls=len(successful),
        success_rate=success_rate_global,
        restored_model=original_model,
        per_model=per_model,
        charts=charts,
    )
    write_json(
        ARTICLES_FILE,
        {
            "query": QUERY,
            "models": models,
            "total_runs": len(rows),
            "articles_by_model": per_model_articles,
        },
    )

    print("\nBenchmark completato.")
    print(f"- Articoli e metriche (JSON): {ARTICLES_FILE}")
    print(f"- Report metriche e grafici (HTML): {METRICS_FILE}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
