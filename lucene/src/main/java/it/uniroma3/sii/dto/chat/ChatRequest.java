package it.uniroma3.sii.dto.chat;

import it.uniroma3.sii.model.ChatScopeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {
    private String sessionId;
    private ChatScopeType scopeType;
    private String scopeId;
    private String message;
}
