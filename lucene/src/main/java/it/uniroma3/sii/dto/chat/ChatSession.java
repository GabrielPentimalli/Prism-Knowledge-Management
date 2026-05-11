package it.uniroma3.sii.dto.chat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import it.uniroma3.sii.model.ChatScopeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
    private String id;
    private String name;
    private ChatScopeType scopeType;
    private String scopeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ChatMessage> messages = new ArrayList<>();
}
