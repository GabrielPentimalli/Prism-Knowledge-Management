package it.uniroma3.sii.service.chat;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import it.uniroma3.sii.dto.chat.ChatMessage;
import it.uniroma3.sii.dto.chat.ChatRequest;
import it.uniroma3.sii.dto.chat.ChatResponse;
import it.uniroma3.sii.dto.chat.ChatSessionSummary;
import it.uniroma3.sii.model.ChatScopeType;
import it.uniroma3.sii.model.PersistentChatSession;
import it.uniroma3.sii.service.storage.ChatSessionRepository;
import it.uniroma3.sii.service.storage.PrivacyLogService;

@Service
public class ChatService {

    private final RetrievalAgentService retrievalAgent;
    private final SynthesisAgentService synthesisAgent;
    private final VerificationAgentService verificationAgent;
    private final QueryRewriteService queryRewriteService;
    private final ChatSessionRepository chatSessionRepository;
    private final PrivacyLogService privacyLogService;

    public ChatService(
            RetrievalAgentService retrievalAgent,
            SynthesisAgentService synthesisAgent,
            VerificationAgentService verificationAgent,
            QueryRewriteService queryRewriteService,
            ChatSessionRepository chatSessionRepository,
            PrivacyLogService privacyLogService) {
        this.retrievalAgent = retrievalAgent;
        this.synthesisAgent = synthesisAgent;
        this.verificationAgent = verificationAgent;
        this.queryRewriteService = queryRewriteService;
        this.chatSessionRepository = chatSessionRepository;
        this.privacyLogService = privacyLogService;
    }

    public ChatResponse processMessage(ChatRequest request) {
        validateScope(request);

        PersistentChatSession session = resolveSession(request);

        // Cronologia PRIMA di aggiungere il messaggio corrente: serve per
        // risolvere i follow-up ("approfondisci", "e il secondo?") sia nel
        // retrieval sia nella sintesi.
        List<ChatMessage> priorHistory = List.copyOf(session.getMessages());

        ChatMessage userMessage = ChatMessage.builder()
                .role("user")
                .content(request.getMessage())
                .timestamp(LocalDateTime.now())
                .citations(List.of())
                .build();
        session.getMessages().add(userMessage);

        String historyText = renderHistory(priorHistory);
        // Espande il follow-up in una domanda autonoma usando la conversazione.
        String standaloneQuestion = queryRewriteService.rewrite(historyText, request.getMessage());
        String retrievalQuery = buildRetrievalQuery(priorHistory, standaloneQuestion);

        var retrieved = retrievalAgent.retrieve(request.getScopeType(), request.getScopeId(), retrievalQuery);
        StructuredAnswer synthesized = synthesisAgent.synthesize(historyText, standaloneQuestion, retrieved);
        StructuredAnswer verified = verificationAgent.verify(synthesized, retrieved);

        ChatMessage assistantMessage = ChatMessage.builder()
                .role("assistant")
                .content(verified.getAnswer())
                .timestamp(LocalDateTime.now())
                .citations(verified.getCitations())
                .build();
        session.getMessages().add(assistantMessage);
        chatSessionRepository.save(session);

        privacyLogService.log("Chat locale scope=" + session.getScopeType() + ":" + session.getScopeId() + " session=" + session.getId());
        return ChatResponse.builder()
                .sessionId(session.getId())
                .answer(verified.getAnswer())
                .citations(verified.getCitations())
                .success(true)
                .build();
    }

    public List<ChatSessionSummary> listSessions(ChatScopeType scopeType, String scopeId) {
        if (scopeType == null || scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeType e scopeId sono obbligatori.");
        }
        return chatSessionRepository.list(scopeType, scopeId).stream()
                .map(s -> ChatSessionSummary.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .scopeType(s.getScopeType())
                        .scopeId(s.getScopeId())
                        .createdAt(s.getCreatedAt())
                        .updatedAt(s.getUpdatedAt())
                        .build())
                .toList();
    }

    public List<ChatMessage> history(String sessionId) {
        return chatSessionRepository.findById(sessionId)
                .map(PersistentChatSession::getMessages)
                .orElse(List.of());
    }

    // Per i follow-up la sola frase corrente (es. "approfondisci") non ha
    // sufficiente sovrapposizione lessicale con i chunk: arricchiamo la query
    // di retrieval con i turni recenti (le risposte precedenti contengono i
    // termini tematici utili al match BM25).
    private static final int RETRIEVAL_CONTEXT_TURNS = 4;
    private static final int HISTORY_TURNS = 6;
    private static final int MSG_CHAR_CAP = 600;

    private String buildRetrievalQuery(List<ChatMessage> priorHistory, String current) {
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, priorHistory.size() - RETRIEVAL_CONTEXT_TURNS);
        for (int i = from; i < priorHistory.size(); i++) {
            ChatMessage m = priorHistory.get(i);
            if (isUsableContext(m)) {
                sb.append(cap(m.getContent())).append(' ');
            }
        }
        sb.append(current);
        return sb.toString();
    }

    private String renderHistory(List<ChatMessage> priorHistory) {
        if (priorHistory.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, priorHistory.size() - HISTORY_TURNS);
        for (int i = from; i < priorHistory.size(); i++) {
            ChatMessage m = priorHistory.get(i);
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            String who = "assistant".equals(m.getRole()) ? "Assistente" : "Utente";
            sb.append(who).append(": ").append(cap(m.getContent())).append('\n');
        }
        return sb.toString().strip();
    }

    // Esclude dal contesto di retrieval i rifiuti precedenti, che non
    // aggiungono termini tematici utili.
    private boolean isUsableContext(ChatMessage m) {
        if (m.getContent() == null || m.getContent().isBlank()) {
            return false;
        }
        return !m.getContent().strip().toLowerCase()
                .contains(SynthesisAgentService.INSUFFICIENT);
    }

    private String cap(String s) {
        String t = s.strip();
        return t.length() <= MSG_CHAR_CAP ? t : t.substring(0, MSG_CHAR_CAP);
    }

    private PersistentChatSession resolveSession(ChatRequest request) {
        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            return chatSessionRepository.create(request.getScopeType(), request.getScopeId());
        }
        PersistentChatSession existing = chatSessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Sessione chat non trovata: " + request.getSessionId()));
        if (existing.getScopeType() != request.getScopeType() || !existing.getScopeId().equals(request.getScopeId())) {
            throw new IllegalArgumentException("La sessione richiesta non appartiene allo scope indicato.");
        }
        return existing;
    }

    private void validateScope(ChatRequest request) {
        if (request.getScopeType() == null) {
            throw new IllegalArgumentException("scopeType è obbligatorio.");
        }
        if (request.getScopeId() == null || request.getScopeId().isBlank()) {
            throw new IllegalArgumentException("scopeId è obbligatorio.");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("Il messaggio non può essere vuoto.");
        }
    }
}
