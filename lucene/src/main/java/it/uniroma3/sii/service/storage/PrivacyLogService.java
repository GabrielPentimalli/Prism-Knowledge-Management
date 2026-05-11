package it.uniroma3.sii.service.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class PrivacyLogService {

    private final StoragePathService paths;

    public PrivacyLogService(StoragePathService paths) {
        this.paths = paths;
    }

    public void log(String action) {
        Path logFile = paths.logsDir().resolve("privacy.log");
        paths.ensureBaseDirectories();
        String line = LocalDateTime.now() + " | " + action + System.lineSeparator();
        try {
            Files.writeString(
                    logFile,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile scrivere privacy.log", e);
        }
    }

    public List<String> tail(int maxLines) {
        Path logFile = paths.logsDir().resolve("privacy.log");
        if (!Files.exists(logFile)) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            if (lines.size() <= maxLines) {
                return lines;
            }
            return lines.subList(lines.size() - maxLines, lines.size());
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere privacy.log", e);
        }
    }
}
