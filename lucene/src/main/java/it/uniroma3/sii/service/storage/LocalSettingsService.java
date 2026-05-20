package it.uniroma3.sii.service.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.uniroma3.sii.dto.OnboardingInitRequest;
import it.uniroma3.sii.dto.SettingsUpdateRequest;
import it.uniroma3.sii.model.PrismSettings;

@Service
public class LocalSettingsService {

    private final ObjectMapper objectMapper;
    private final String defaultDataRoot;
    private final Path bootstrapSettingsFile;
    private final String defaultLlmBaseUrl;
    private final String defaultLlmModel;

    private PrismSettings cached;

    public LocalSettingsService(
            ObjectMapper objectMapper,
            @Value("${prism.storage.default-data-root}") String defaultDataRoot,
            @Value("${prism.storage.bootstrap-file}") String bootstrapFile,
            @Value("${llm.base-url}") String defaultLlmBaseUrl,
            @Value("${llm.model}") String defaultLlmModel) {
        this.objectMapper = objectMapper;
        this.defaultDataRoot = defaultDataRoot;
        this.bootstrapSettingsFile = Paths.get(bootstrapFile).toAbsolutePath().normalize();
        this.defaultLlmBaseUrl = defaultLlmBaseUrl;
        this.defaultLlmModel = defaultLlmModel;
    }

    public synchronized PrismSettings getSettings() {
        if (cached != null) {
            return cached;
        }
        cached = loadSettings();
        return cached;
    }

    public synchronized boolean isOnboardingCompleted() {
        return getSettings().isOnboardingCompleted();
    }

    public synchronized PrismSettings initialize(OnboardingInitRequest request) {
        String dataRoot = normalizeDataRoot(request.getDataRoot());
        PrismSettings settings = PrismSettings.builder()
                .onboardingCompleted(true)
                .dataRoot(dataRoot)
                .llmBaseUrl(normalizeUrl(request.getLlmBaseUrl(), defaultLlmBaseUrl))
                .llmModel(normalizeText(request.getLlmModel(), defaultLlmModel))
                .language("it")
                .localMode(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        persistSettings(settings);
        cached = settings;
        return settings;
    }

    public synchronized PrismSettings update(SettingsUpdateRequest request) {
        PrismSettings current = getSettings();
        PrismSettings updated = PrismSettings.builder()
                .onboardingCompleted(current.isOnboardingCompleted())
                .dataRoot(normalizeDataRoot(request.getDataRoot() != null ? request.getDataRoot() : current.getDataRoot()))
                .llmBaseUrl(normalizeUrl(request.getLlmBaseUrl(), current.getLlmBaseUrl()))
                .llmModel(normalizeText(request.getLlmModel(), current.getLlmModel()))
                .language("it")
                .localMode(true)
                .createdAt(current.getCreatedAt() == null ? LocalDateTime.now() : current.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();
        persistSettings(updated);
        cached = updated;
        return updated;
    }

    public synchronized Path settingsFileInDataRoot() {
        Path dataRoot = Paths.get(getSettings().getDataRoot()).toAbsolutePath().normalize();
        return dataRoot.resolve("config").resolve("settings.json");
    }

    private PrismSettings loadSettings() {
        PrismSettings bootstrap = readIfExists(bootstrapSettingsFile);
        if (bootstrap != null && bootstrap.getDataRoot() != null && !bootstrap.getDataRoot().isBlank()) {
            Path fileInRoot = Paths.get(bootstrap.getDataRoot()).toAbsolutePath().normalize()
                    .resolve("config")
                    .resolve("settings.json");
            PrismSettings inRoot = readIfExists(fileInRoot);
            if (inRoot != null) {
                return enforceItalianLanguage(inRoot);
            }
            return enforceItalianLanguage(bootstrap);
        }
        PrismSettings fallback = defaults();
        persistSettings(fallback);
        return fallback;
    }

    private PrismSettings defaults() {
        return PrismSettings.builder()
                .onboardingCompleted(false)
                .dataRoot(normalizeDataRoot(defaultDataRoot))
                .llmBaseUrl(defaultLlmBaseUrl)
                .llmModel(defaultLlmModel)
                .language("it")
                .localMode(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void persistSettings(PrismSettings settings) {
        try {
            Path dataRoot = Paths.get(settings.getDataRoot()).toAbsolutePath().normalize();
            Path configDir = dataRoot.resolve("config");
            Files.createDirectories(configDir);
            Files.createDirectories(bootstrapSettingsFile.getParent());
            Path settingsFile = configDir.resolve("settings.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile.toFile(), settings);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(bootstrapSettingsFile.toFile(), settings);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile salvare le impostazioni locali", e);
        }
    }

    private PrismSettings readIfExists(Path file) {
        try {
            if (file != null && Files.exists(file)) {
                return objectMapper.readValue(file.toFile(), PrismSettings.class);
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere le impostazioni locali da " + file, e);
        }
    }

    private String normalizeDataRoot(String value) {
        String raw = normalizeText(value, defaultDataRoot);
        return Paths.get(raw).toAbsolutePath().normalize().toString();
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String normalizeUrl(String value, String fallback) {
        String url = normalizeText(value, fallback);
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private PrismSettings enforceItalianLanguage(PrismSettings settings) {
        if (settings == null || "it".equalsIgnoreCase(settings.getLanguage())) {
            return settings;
        }
        settings.setLanguage("it");
        settings.setUpdatedAt(LocalDateTime.now());
        persistSettings(settings);
        return settings;
    }
}
