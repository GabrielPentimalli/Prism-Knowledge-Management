package it.uniroma3.sii.dto.chat;

import java.time.LocalDateTime;
import java.util.List;

import it.uniroma3.sii.model.Citation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    private String role;
    private String content;
    private LocalDateTime timestamp;
    private List<Citation> citations;
}
