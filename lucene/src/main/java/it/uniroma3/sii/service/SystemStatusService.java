package it.uniroma3.sii.service;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.uniroma3.sii.dto.SystemStatusResponse;
import it.uniroma3.sii.model.PrismSettings;
import it.uniroma3.sii.service.chat.LlmClient;
import it.uniroma3.sii.service.storage.LocalSettingsService;
import it.uniroma3.sii.service.storage.StoragePathService;

@Service
public class SystemStatusService {

    private final LocalSettingsService settingsService;
    private final StoragePathService paths;
    private final LlmClient llmClient;
    private final long minimumDiskMb;

    public SystemStatusService(
            LocalSettingsService settingsService,
            StoragePathService paths,
            LlmClient llmClient,
            @Value("${prism.minimum-free-disk-mb}") long minimumDiskMb) {
        this.settingsService = settingsService;
        this.paths = paths;
        this.llmClient = llmClient;
        this.minimumDiskMb = minimumDiskMb;
    }

    public SystemStatusResponse status() {
        PrismSettings settings = settingsService.getSettings();
        long freeMb = freeDiskMb();
        boolean diskOk = freeMb >= minimumDiskMb;
        boolean modelAvailable = llmClient.isModelAvailable();
        return SystemStatusResponse.builder()
                .localModeActive(true)
                .onboardingCompleted(settings.isOnboardingCompleted())
                .modelAvailable(modelAvailable)
                .diskOk(diskOk)
                .freeDiskMb(freeMb)
                .requiredDiskMb(minimumDiskMb)
                .model(settings.getLlmModel())
                .dataRoot(settings.getDataRoot())
                .build();
    }

    private long freeDiskMb() {
        try {
            paths.ensureBaseDirectories();
            FileStore store = Files.getFileStore(paths.dataRoot());
            return store.getUsableSpace() / (1024 * 1024);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere lo spazio disco disponibile", e);
        }
    }
}
