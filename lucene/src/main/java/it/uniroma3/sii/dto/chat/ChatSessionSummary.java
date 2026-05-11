package it.uniroma3.sii.dto.chat;

import java.time.LocalDateTime;

import it.uniroma3.sii.model.ChatScopeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionSummary {
    private String id;
    private String name;
    private ChatScopeType scopeType;
    private String scopeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
