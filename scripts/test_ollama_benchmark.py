import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

import ollama_benchmark as benchmark


def response(data):
    result = MagicMock()
    result.__enter__.return_value = result
    result.read.return_value = json.dumps(data).encode()
    result.status = 200
    return result


class BenchmarkContractTest(unittest.TestCase):
    def test_chat_request_contains_required_scope(self):
        with patch.object(benchmark.urlrequest, "urlopen", return_value=response({"answer": "ok"})) as request:
            result = benchmark.call_chatbot("question", 5, "VAULT", "vault-1")
        payload = json.loads(request.call_args.args[0].data)
        self.assertEqual(payload["scopeType"], "VAULT")
        self.assertEqual(payload["scopeId"], "vault-1")
        self.assertEqual(result["answer"], "ok")

    def test_readiness_uses_endpoint_without_required_query_parameters(self):
        with patch.object(benchmark.urlrequest, "urlopen", return_value=response([])) as request:
            benchmark.wait_backend_ready(1)
        self.assertEqual(request.call_args.args[0].full_url, benchmark.VAULTS_API_URL)

    def test_runtime_configuration_uses_settings_api(self):
        with patch.object(benchmark.urlrequest, "urlopen", return_value=response({"settings": {"llmModel": "original"}})) as request:
            self.assertEqual(benchmark.get_runtime_model(), "original")
            benchmark.set_runtime_model("candidate")
        update = request.call_args.args[0]
        self.assertEqual(update.get_method(), "PUT")
        self.assertEqual(json.loads(update.data), {"llmModel": "candidate"})

    def test_prism_citations_keep_document_names_and_ids(self):
        result = benchmark.extract_articles({"citations": [{"docId": "doc-1", "docName": "Document"}]}, "answer")
        self.assertEqual(result[0]["title"], "Document")
        self.assertEqual(result[0]["id"], "doc-1")

    def test_run_uses_answer_and_restores_persisted_model_even_on_chat_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            props = root / "application.properties"
            props.write_text("llm.model=properties-original\n")
            with patch.multiple(benchmark, OUTPUT_DIR=root, APP_PROPS_PATH=props,
                                ARTICLES_FILE=root / "articles.json", METRICS_FILE=root / "report.html"), \
                    patch.object(benchmark, "get_models_from_ollama_list", return_value=["first", "second"]), \
                    patch.object(benchmark, "is_port_open", return_value=False), \
                    patch.object(benchmark, "start_backend"), \
                    patch.object(benchmark, "stop_backend") as stop, \
                    patch.object(benchmark, "get_runtime_model", return_value="persisted-original"), \
                    patch.object(benchmark, "set_runtime_model") as configure, \
                    patch.object(benchmark, "call_chatbot", side_effect=[
                        {"answer": "actual answer", "success": True, "sessionId": "session-1"},
                        RuntimeError("generation failed")]) as chat, \
                    patch.object(benchmark, "build_charts", return_value={}):
                self.assertEqual(benchmark.main("DOCUMENT", "doc-1"), 0)

            self.assertEqual([call.args[0] for call in configure.call_args_list],
                             ["first", "persisted-original", "second", "persisted-original"])
            self.assertEqual(chat.call_args.args[2:], ("DOCUMENT", "doc-1"))
            self.assertEqual(stop.call_count, 2)
            self.assertEqual(props.read_text(), "llm.model=properties-original\n")
            report = json.loads((root / "articles.json").read_text())
            first_run = report["articles_by_model"][0]["runs"][0]
            self.assertEqual(first_run["articles"][0]["title"], "actual answer")
            second_run = report["articles_by_model"][1]["runs"][0]
            self.assertFalse(second_run["success"])


if __name__ == "__main__":
    unittest.main()
