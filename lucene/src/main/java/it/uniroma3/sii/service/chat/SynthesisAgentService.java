package it.uniroma3.sii.service.chat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.uniroma3.sii.model.Citation;
import it.uniroma3.sii.service.indexing.ChunkIndexService;

@Service
public class SynthesisAgentService {

    private static final Logger log = LoggerFactory.getLogger(SynthesisAgentService.class);

    static final String INSUFFICIENT = "non ho evidenza sufficiente nei documenti caricati";

    // I modelli locali piccoli (es. mistral-nemo:12b) non riescono a ricopiare
    // verbatim i chunkId lunghi: citano i frammenti per NUMERO e mappiamo noi
    // l'indice al chunk reale lato server.
    private static final String PROMPT = """
            Sei l'assistente di PRISM. Il messaggio dell'utente contiene dei FRAMMENTI numerati
            estratti dai SUOI documenti: quei frammenti sono il tuo unico materiale di lavoro.
            Hai pieno accesso a essi: NON dire MAI di non avere accesso ai documenti o alle fonti.

            Rispondi SEMPRE e SOLO in ITALIANO, anche se i frammenti sono scritti in inglese
            (in tal caso traduci/sintetizza il loro contenuto in italiano).

            Se è presente una CONVERSAZIONE PRECEDENTE, usala per capire a cosa si riferisce
            la domanda (es. "approfondisci", "e il secondo documento?", "e quali emozioni studia?"):
            la domanda di follow-up va interpretata alla luce dei turni precedenti.

            Restituisci un oggetto JSON con ESATTAMENTE due campi:
              - "answer": la risposta in italiano
              - "sources": array di numeri interi, gli indici dei frammenti effettivamente usati (es. [1, 3])

            Linee guida:
            - Per domande comparative ("cosa hanno in comune?", "differenze?") confronta frammenti di documenti diversi.
            - Per riassunti, sintetizza i frammenti pertinenti.
            - Basati solo sui frammenti, senza inventare.

            Esempio: {"answer": "La depressione aumenta il rischio di suicidio.", "sources": [1, 2]}

            Solo se NESSUN frammento è pertinente alla domanda rispondi:
            {"answer": "non ho evidenza sufficiente nei documenti caricati", "sources": []}.
            """;

    // Chiavi tollerate: difesa in profondità se il modello deviasse dallo schema.
    private static final List<String> ANSWER_KEYS =
            List.of("answer", "risposta", "response", "text", "synthesized_text", "summary", "content");
    private static final List<String> SOURCE_KEYS =
            List.of("sources", "source", "citations", "cited", "fragments", "indices");

    // Structured output: vincola il modello a produrre esattamente {answer, sources}.
    private static final java.util.Map<String, Object> ANSWER_SCHEMA = java.util.Map.of(
            "type", "object",
            "properties", java.util.Map.of(
                    "answer", java.util.Map.of("type", "string"),
                    "sources", java.util.Map.of(
                            "type", "array",
                            "items", java.util.Map.of("type", "integer"))),
            "required", List.of("answer", "sources"),
            "additionalProperties", false);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public SynthesisAgentService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public StructuredAnswer synthesize(String userMessage, List<ChunkIndexService.ChunkHit> chunks) {
        return synthesize("", userMessage, chunks);
    }

    public StructuredAnswer synthesize(String historyText, String userMessage,
                                       List<ChunkIndexService.ChunkHit> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.debug("Synthesis: nessun chunk in input, restituisco fallback.");
            return insufficient();
        }
        Set<String> docsInScope = chunks.stream()
                .map(ChunkIndexService.ChunkHit::docName)
                .collect(Collectors.toSet());
        log.debug("Synthesis: {} chunk da {} documenti distinti, history={}.",
                chunks.size(), docsInScope.size(), historyText == null || historyText.isBlank() ? "no" : "si");

        String payload = buildPayload(historyText, userMessage, chunks);
        String raw = llmClient.chatStructured(PROMPT, payload, "prism_answer", ANSWER_SCHEMA);
        log.trace("Synthesis raw response: {}", raw);
        return parse(raw, chunks);
    }

    private StructuredAnswer parse(String raw, List<ChunkIndexService.ChunkHit> chunks) {
        if (raw == null || raw.isBlank()) {
            log.debug("Synthesis: LLM ha risposto vuoto -> insufficient.");
            return insufficient();
        }

        String stripped = stripFences(raw).strip();
        JsonNode root = tryParseJson(stripped);

        String answer = null;
        List<Integer> sources = List.of();
        if (root != null) {
            answer = findAnswer(root);
            sources = findSources(root);
        }

        // Degradazione graziosa: il modello ha risposto in prosa (non JSON).
        // Meglio restituire quella risposta che un falso "non ho evidenza".
        // Se invece è un JSON senza campo-risposta riconoscibile (spesso una
        // struttura inventata), NON usiamo il blob come risposta per non
        // esporre eventuali fabbricazioni.
        if (isBlank(answer) && !looksLikeJson(stripped)) {
            answer = stripped;
        }

        if (isBlank(answer)) {
            log.debug("Synthesis: nessuna risposta estraibile (raw preview: {}).", preview(raw));
            return insufficient();
        }
        if (isRefusal(answer)) {
            log.debug("Synthesis: il modello dichiara evidenza insufficiente.");
            return insufficient();
        }

        StructuredAnswer out = new StructuredAnswer();
        out.setAnswer(answer.strip());
        out.setCitations(buildCitations(sources, chunks));
        log.debug("Synthesis parsed: answer-len={} sources={} citations={}",
                out.getAnswer().length(), sources.size(), out.getCitations().size());
        return out;
    }

    private List<Citation> buildCitations(List<Integer> sources, List<ChunkIndexService.ChunkHit> chunks) {
        List<Citation> citations = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (Integer idx : sources) {
            if (idx == null || idx < 1 || idx > chunks.size() || !seen.add(idx)) {
                continue;
            }
            ChunkIndexService.ChunkHit hit = chunks.get(idx - 1);
            citations.add(Citation.builder()
                    .docId(hit.docId())
                    .docName(hit.docName())
                    .page(hit.page())
                    .paragraphIndex(hit.paragraphIndex())
                    .chunkId(hit.chunkId())
                    .score((double) hit.score())
                    .build());
        }
        return citations;
    }

    private String buildPayload(String historyText, String userMessage,
                                List<ChunkIndexService.ChunkHit> chunks) {
        StringBuilder sb = new StringBuilder();
        if (historyText != null && !historyText.isBlank()) {
            sb.append("CONVERSAZIONE PRECEDENTE (per interpretare i riferimenti della domanda):\n");
            sb.append(historyText).append("\n\n");
        }
        sb.append("Documenti dell'utente rappresentati nei frammenti: ");
        sb.append(chunks.stream()
                .map(ChunkIndexService.ChunkHit::docName)
                .distinct()
                .collect(Collectors.joining(", ")));
        sb.append("\n\nFRAMMENTI (sono i documenti dell'utente, hai pieno accesso):\n");
        int i = 1;
        for (ChunkIndexService.ChunkHit hit : chunks) {
            sb.append("[").append(i++).append("] ");
            sb.append("(documento: ").append(hit.docName());
            sb.append(", pagina ").append(hit.page()).append(")\n");
            sb.append(hit.text()).append("\n\n");
        }
        sb.append("Domanda dell'utente: ").append(userMessage).append("\n");
        sb.append("Rispondi in italiano usando i frammenti qui sopra.\n");
        return sb.toString();
    }

    private JsonNode tryParseJson(String text) {
        String json = extractJsonObject(text);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJsonObject(String cleaned) {
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return null;
    }

    private String findAnswer(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            for (String key : ANSWER_KEYS) {
                JsonNode value = node.get(key);
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                String found = findAnswer(fields.next().getValue());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private List<Integer> findSources(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        if (node.isObject()) {
            for (String key : SOURCE_KEYS) {
                JsonNode value = node.get(key);
                if (value != null) {
                    List<Integer> ints = coerceInts(value);
                    if (!ints.isEmpty()) {
                        return ints;
                    }
                }
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                List<Integer> found = findSources(fields.next().getValue());
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return List.of();
    }

    private List<Integer> coerceInts(JsonNode value) {
        List<Integer> out = new ArrayList<>();
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (item.isInt() || item.isLong()) {
                    out.add(item.asInt());
                } else if (item.isTextual() && item.asText().strip().matches("\\d+")) {
                    out.add(Integer.parseInt(item.asText().strip()));
                } else if (item.isObject()) {
                    for (String k : List.of("index", "n", "id", "source", "number")) {
                        JsonNode inner = item.get(k);
                        if (inner != null && (inner.isInt() || inner.isLong())) {
                            out.add(inner.asInt());
                            break;
                        }
                    }
                }
            }
        } else if (value.isInt() || value.isLong()) {
            out.add(value.asInt());
        }
        return out;
    }

    private boolean looksLikeJson(String text) {
        String t = text.strip();
        return t.startsWith("{") || t.startsWith("[");
    }

    private boolean isRefusal(String answer) {
        return answer != null && answer.strip().toLowerCase().contains(INSUFFICIENT);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String stripFences(String raw) {
        return raw.replace("```json", "").replace("```", "");
    }

    private String preview(String raw) {
        return raw == null ? "null" : raw.substring(0, Math.min(200, raw.length()));
    }

    private StructuredAnswer insufficient() {
        StructuredAnswer answer = new StructuredAnswer();
        answer.setAnswer(INSUFFICIENT);
        answer.setCitations(List.of());
        return answer;
    }
}
