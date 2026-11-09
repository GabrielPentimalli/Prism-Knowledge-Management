package it.uniroma3.sii.service.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.uniroma3.sii.model.ChatScopeType;
import it.uniroma3.sii.model.PersistentChatSession;

@Repository
public class ChatSessionRepository {

    private final StoragePathService paths;
    private final ObjectMapper objectMapper;

    public ChatSessionRepository(StoragePathService paths, ObjectMapper objectMapper) {
        this.paths = paths;
        this.objectMapper = objectMapper;
    }

    public PersistentChatSession create(ChatScopeType scopeType, String scopeId) {
        PersistentChatSession session = PersistentChatSession.builder()
                .id(UUID.randomUUID().toString())
                .name(defaultName(scopeType))
                .scopeType(scopeType)
                .scopeId(scopeId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return save(session);
    }

    public PersistentChatSession save(PersistentChatSession session) {
        try {
            paths.ensureBaseDirectories();
            session.setUpdatedAt(LocalDateTime.now());
            Path dir = scopeDir(session.getScopeType(), session.getScopeId());
            Files.createDirectories(dir);
            JsonStorage.write(objectMapper, StorageId.resolve(dir, StorageId.validate(session.getId()) + ".json"), session);
            return session;
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile salvare la sessione chat " + session.getId(), e);
        }
    }

    public Optional<PersistentChatSession> findById(String sessionId) {
        StorageId.validate(sessionId);
        Path root = paths.chatsDir();
        if (!Files.exists(root)) {
            return Optional.empty();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(sessionId + ".json"))
                    .findFirst()
                    .map(this::readSession);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile cercare la sessione chat " + sessionId, e);
        }
    }

    public List<PersistentChatSession> list(ChatScopeType scopeType, String scopeId) {
        Path dir = scopeDir(scopeType, scopeId);
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readSession)
                    .sorted(Comparator.comparing(PersistentChatSession::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile elencare sessioni chat per scope " + scopeType + "/" + scopeId, e);
        }
    }

    public void deleteScope(ChatScopeType scopeType, String scopeId) {
        Path dir = scopeDir(scopeType, scopeId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Impossibile eliminare " + path, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile eliminare le chat per scope " + scopeType + "/" + scopeId, e);
        }
    }

    private Path scopeDir(ChatScopeType scopeType, String scopeId) {
        if (scopeType == null) {
            throw new IllegalArgumentException("scopeType è obbligatorio.");
        }
        return StorageId.resolve(paths.chatsDir().resolve(scopeType.name().toLowerCase(java.util.Locale.ROOT)), scopeId);
    }

    private String defaultName(ChatScopeType scopeType) {
        return scopeType == ChatScopeType.DOCUMENT ? "Chat documento" : "Chat vault";
    }

    private PersistentChatSession readSession(Path file) {
        try {
            return objectMapper.readValue(file.toFile(), PersistentChatSession.class);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere la sessione chat " + file, e);
        }
    }
}
