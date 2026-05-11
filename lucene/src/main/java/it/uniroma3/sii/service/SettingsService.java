package it.uniroma3.sii.service;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import it.uniroma3.sii.dto.OnboardingInitRequest;
import it.uniroma3.sii.dto.SettingsUpdateRequest;
import it.uniroma3.sii.model.PrismSettings;
import it.uniroma3.sii.service.chat.LlmClient;
import it.uniroma3.sii.service.storage.LocalSettingsService;
import it.uniroma3.sii.service.storage.PrivacyLogService;
import it.uniroma3.sii.service.storage.StoragePathService;

@Service
public class SettingsService {

    private final LocalSettingsService localSettingsService;
    private final StoragePathService pathService;
    private final PrivacyLogService privacyLogService;
    private final LlmClient llmClient;

    public SettingsService(
            LocalSettingsService localSettingsService,
            StoragePathService pathService,
            PrivacyLogService privacyLogService,
            LlmClient llmClient) {
        this.localSettingsService = localSettingsService;
        this.pathService = pathService;
        this.privacyLogService = privacyLogService;
        this.llmClient = llmClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncLlmClientOnStartup() {
        PrismSettings settings = localSettingsService.getSettings();
        applyLlmConfig(settings);
    }

    public PrismSettings initialize(OnboardingInitRequest request) {
        PrismSettings settings = localSettingsService.initialize(request);
        pathService.ensureBaseDirectories();
        applyLlmConfig(settings);
        privacyLogService.log("Onboarding completato. Data root: " + settings.getDataRoot());
        return settings;
    }

    public PrismSettings getSettings() {
        return localSettingsService.getSettings();
    }

    public PrismSettings update(SettingsUpdateRequest request) {
        PrismSettings updated = localSettingsService.update(request);
        pathService.ensureBaseDirectories();
        applyLlmConfig(updated);
        privacyLogService.log("Impostazioni aggiornate");
        return updated;
    }

    public boolean isOnboardingCompleted() {
        return localSettingsService.isOnboardingCompleted();
    }

    public Map<String, Object> settingsWithPrivacyLog() {
        PrismSettings settings = getSettings();
        List<String> logTail = privacyLogService.tail(200);
        return Map.of(
                "settings", settings,
                "privacyLog", logTail,
                "networkPolicy", "Solo operazioni locali. Consentito esclusivamente localhost per LLM.");
    }

    private void applyLlmConfig(PrismSettings settings) {
        if (settings == null) {
            return;
        }
        String baseUrl = settings.getLlmBaseUrl();
        String model = settings.getLlmModel();
        if (baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) {
            return;
        }
        try {
            llmClient.configure(baseUrl, model);
        } catch (RuntimeException e) {
            privacyLogService.log("Aggiornamento LLM rifiutato: " + e.getMessage());
            throw e;
        }
    }
}
