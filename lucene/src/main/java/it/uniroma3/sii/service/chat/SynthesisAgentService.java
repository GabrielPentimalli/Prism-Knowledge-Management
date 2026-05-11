package it.uniroma3.sii.service.chat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.uniroma3.sii.service.indexing.ChunkIndexService;

@Service
public class SynthesisAgentService {

    private static final Logger log = LoggerFactory.getLogger(SynthesisAgentService.class);

    private static final String PROMPT = """
            Sei il synthesis agent di PRISM. Il tuo compito è rispondere alla domanda dell'utente
            usando ESCLUSIVAMENTE i chunk forniti come evidenza, citandoli con il loro chunkId.

            Regole:
            1. Non inventare informazioni non presenti nei chunk.
            2. Per domande comparative (es. "cosa hanno in comune?", "differenze?"), analizza chunk
               provenienti da TUTTI i documenti citati e produci un confronto strutturato.
            3. Per domande di sintesi, riassumi i chunk pertinenti.
            4. Per domande puntuali, rispondi citando i chunk specifici.
            5. Cita SEMPRE almeno un chunkId presente nell'elenco fornito, anche per risposte brevi.
            6. Se i chunk forniti non contengono alcuna informazione rilevante per rispondere,
               imposta answer esattamente a "non ho evidenza sufficiente nei documenti caricati"
               e restituisci citations come array vuoto.

            Rispondi SOLO in JSON valido con questo schema, senza markdown, senza spiegazioni:
            {
              "answer": "testo risposta in italiano",
              "citations": [
                {"docId":"...", "docName":"...", "page":1, "paragraphIndex":2, "chunkId":"...", "score":0.9}
              ]
            }
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public SynthesisAgentService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public StructuredAnswer synthesize(String userMessage, List<ChunkIndexService.ChunkHit> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            log.debug("Synthesis: nessun chunk in input, restituisco fallback.");
            return insufficient();
        }
        Set<String> docsInScope = chunks.stream()
                .map(ChunkIndexService.ChunkHit::docName)
                .collect(Collectors.toSet());
        log.debug("Synthesis: {} chunk da {} documenti distinti.", chunks.size(), docsInScope.size());

        String payload = buildPayload(userMessage, chunks);
        String raw = llmClient.chat(PROMPT, payload);
        log.trace("Synthesis raw response: {}", raw);
        String json = extractJson(raw);
        try {
            StructuredAnswer parsed = objectMapper.readValue(json, StructuredAnswer.class);
            log.debug("Synthesis parsed: answer-len={} citations={}",
                    parsed.getAnswer() == null ? 0 : parsed.getAnswer().length(),
                    parsed.getCitations() == null ? 0 : parsed.getCitations().size());
            return parsed;
        } catch (Exception e) {
            log.warn("Synthesis JSON non parsabile: {} (raw preview: {})",
                    e.getMessage(),
                    raw == null ? "null" : raw.substring(0, Math.min(200, raw.length())));
            return insufficient();
        }
    }

    private String buildPayload(String userMessage, List<ChunkIndexService.ChunkHit> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Domanda utente: ").append(userMessage).append("\n\n");
        sb.append("Documenti rappresentati nei chunk: ");
        sb.append(chunks.stream()
                .map(ChunkIndexService.ChunkHit::docName)
                .distinct()
                .collect(Collectors.joining(", ")));
        sb.append("\n\nChunk disponibili:\n");
        for (ChunkIndexService.ChunkHit hit : chunks) {
            sb.append("---\n");
            sb.append("chunkId: ").append(hit.chunkId()).append("\n");
            sb.append("docId: ").append(hit.docId()).append("\n");
            sb.append("docName: ").append(hit.docName()).append("\n");
            sb.append("page: ").append(hit.page()).append("\n");
            sb.append("paragraphIndex: ").append(hit.paragraphIndex()).append("\n");
            sb.append("score: ").append(hit.score()).append("\n");
            sb.append("text: ").append(hit.text()).append("\n");
        }
        sb.append("---\n");
        return sb.toString();
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{\"answer\":\"non ho evidenza sufficiente nei documenti caricati\",\"citations\":[]}";
        }
        String cleaned = raw.strip().replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return "{\"answer\":\"non ho evidenza sufficiente nei documenti caricati\",\"citations\":[]}";
    }

    private StructuredAnswer insufficient() {
        StructuredAnswer answer = new StructuredAnswer();
        answer.setAnswer("non ho evidenza sufficiente nei documenti caricati");
        answer.setCitations(List.of());
        return answer;
    }
}
