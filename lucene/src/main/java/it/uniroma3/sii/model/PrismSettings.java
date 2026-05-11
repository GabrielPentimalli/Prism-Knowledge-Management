package it.uniroma3.sii.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrismSettings {
    private boolean onboardingCompleted;
    private String dataRoot;
    private String llmBaseUrl;
    private String llmModel;
    private String language;
    private boolean localMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
