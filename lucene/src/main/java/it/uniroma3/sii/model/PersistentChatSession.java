package it.uniroma3.sii.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import it.uniroma3.sii.dto.chat.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersistentChatSession {
    private String id;
    private String name;
    private ChatScopeType scopeType;
    private String scopeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();
}
