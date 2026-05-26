package it.uniroma3.sii.service.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Riscrive una domanda di follow-up ("approfondisci", "e il secondo?",
 * "quali emozioni studia?") in una domanda AUTONOMA, risolvendo i riferimenti
 * impliciti con la conversazione precedente.
 *
 * Senza questo passaggio i modelli locali piccoli, ricevendo solo la frase
 * vaga, non riescono a collegare il follow-up al contesto e rispondono
 * "non ho evidenza sufficiente". La domanda riscritta migliora sia il
 * retrieval (più termini tematici) sia la sintesi (intento esplicito).
 */
@Service
public class QueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriteService.class);

    private static final String PROMPT = """
            Riscrivi la DOMANDA dell'utente come una domanda autonoma e completa in ITALIANO,
            risolvendo ogni riferimento implicito (pronomi, "questo", "approfondisci", "e il secondo?")
            usando la CONVERSAZIONE PRECEDENTE. Non rispondere alla domanda: riformulala soltanto.
            Se la domanda è già autonoma, restituiscila sostanzialmente invariata.

            Restituisci JSON con un solo campo: {"question": "<domanda autonoma in italiano>"}.
            """;

    private static final java.util.Map<String, Object> SCHEMA = java.util.Map.of(
            "type", "object",
            "properties", java.util.Map.of("question", java.util.Map.of("type", "string")),
            "required", java.util.List.of("question"),
            "additionalProperties", false);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public QueryRewriteService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * @return la domanda riscritta, oppure {@code currentMessage} invariato se
     *         non c'è storia o se la riscrittura non è utilizzabile.
     */
    public String rewrite(String historyText, String currentMessage) {
        if (historyText == null || historyText.isBlank()) {
            return currentMessage;
        }
        String payload = "CONVERSAZIONE PRECEDENTE:\n" + historyText
                + "\n\nDOMANDA da riscrivere: " + currentMessage;
        try {
            String raw = llmClient.chatStructured(PROMPT, payload, "rewritten_query", SCHEMA);
            String rewritten = extractQuestion(raw);
            if (rewritten == null || rewritten.isBlank()) {
                return currentMessage;
            }
            log.debug("Query rewrite: '{}' -> '{}'", currentMessage, rewritten);
            return rewritten.strip();
        } catch (Exception e) {
            log.debug("Query rewrite fallita ({}), uso la domanda originale.", e.getMessage());
            return currentMessage;
        }
    }

    private String extractQuestion(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.strip().replace("```json", "").replace("```", "").strip();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(cleaned.substring(start, end + 1));
            JsonNode q = node.get("question");
            return q != null && q.isTextual() ? q.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
