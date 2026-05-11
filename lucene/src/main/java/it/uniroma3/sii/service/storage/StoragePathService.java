package it.uniroma3.sii.service.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Service;

@Service
public class StoragePathService {

    private final LocalSettingsService settingsService;

    public StoragePathService(LocalSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public Path dataRoot() {
        return Paths.get(settingsService.getSettings().getDataRoot()).toAbsolutePath().normalize();
    }

    public Path configDir() {
        return dataRoot().resolve("config");
    }

    public Path documentsDir() {
        return dataRoot().resolve("documents");
    }

    public Path indicesDir() {
        return dataRoot().resolve("indices");
    }

    public Path vaultsDir() {
        return dataRoot().resolve("vaults");
    }

    public Path chatsDir() {
        return dataRoot().resolve("chats");
    }

    public Path logsDir() {
        return dataRoot().resolve("logs");
    }

    public void ensureBaseDirectories() {
        create(configDir());
        create(documentsDir());
        create(indicesDir());
        create(vaultsDir());
        create(chatsDir());
        create(logsDir());
    }

    private void create(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile creare la directory locale: " + path, e);
        }
    }
}
