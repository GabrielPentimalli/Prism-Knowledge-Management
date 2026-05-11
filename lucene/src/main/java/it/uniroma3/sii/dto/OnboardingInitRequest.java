package it.uniroma3.sii.dto;

import lombok.Data;

@Data
public class OnboardingInitRequest {
    private String dataRoot;
    private String llmModel;
    private String llmBaseUrl;
    private String language;
}
