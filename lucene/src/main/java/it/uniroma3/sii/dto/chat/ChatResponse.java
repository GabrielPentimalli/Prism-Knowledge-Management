package it.uniroma3.sii.dto.chat;

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
public class ChatResponse {
    private String sessionId;
    private String answer;
    private List<Citation> citations;
    private boolean success;
    private String errorMessage;
}
