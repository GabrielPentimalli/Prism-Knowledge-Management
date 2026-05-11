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
    private final ChatSessionRepository chatSessionRepository;
    private final PrivacyLogService privacyLogService;

    public ChatService(
            RetrievalAgentService retrievalAgent,
            SynthesisAgentService synthesisAgent,
            VerificationAgentService verificationAgent,
            ChatSessionRepository chatSessionRepository,
            PrivacyLogService privacyLogService) {
        this.retrievalAgent = retrievalAgent;
        this.synthesisAgent = synthesisAgent;
        this.verificationAgent = verificationAgent;
        this.chatSessionRepository = chatSessionRepository;
        this.privacyLogService = privacyLogService;
    }

    public ChatResponse processMessage(ChatRequest request) {
        validateScope(request);

        PersistentChatSession session = resolveSession(request);
        ChatMessage userMessage = ChatMessage.builder()
                .role("user")
                .content(request.getMessage())
                .timestamp(LocalDateTime.now())
                .citations(List.of())
                .build();
        session.getMessages().add(userMessage);

        var retrieved = retrievalAgent.retrieve(request.getScopeType(), request.getScopeId(), request.getMessage());
        StructuredAnswer synthesized = synthesisAgent.synthesize(request.getMessage(), retrieved);
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
