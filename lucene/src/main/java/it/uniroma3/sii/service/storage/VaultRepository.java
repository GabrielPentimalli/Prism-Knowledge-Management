package it.uniroma3.sii.service.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.uniroma3.sii.model.Vault;

@Repository
public class VaultRepository {

    private final StoragePathService paths;
    private final ObjectMapper objectMapper;

    public VaultRepository(StoragePathService paths, ObjectMapper objectMapper) {
        this.paths = paths;
        this.objectMapper = objectMapper;
    }

    public Vault save(Vault vault) {
        try {
            paths.ensureBaseDirectories();
            if (vault.getCreatedAt() == null) {
                vault.setCreatedAt(LocalDateTime.now());
            }
            vault.setUpdatedAt(LocalDateTime.now());
            Path vaultDir = vaultDir(vault.getId());
            Files.createDirectories(vaultDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(vaultDir.resolve("vault.json").toFile(), vault);
            return vault;
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile salvare il vault " + vault.getId(), e);
        }
    }

    public Optional<Vault> findById(String vaultId) {
        Path file = vaultDir(vaultId).resolve("vault.json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), Vault.class));
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere il vault " + vaultId, e);
        }
    }

    public List<Vault> listAll() {
        Path root = paths.vaultsDir();
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(dir -> dir.resolve("vault.json"))
                    .filter(Files::exists)
                    .map(this::readVault)
                    .sorted(Comparator.comparing(Vault::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile elencare i vault", e);
        }
    }

    public void delete(String vaultId) {
        Path dir = vaultDir(vaultId);
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
            throw new IllegalStateException("Impossibile eliminare il vault " + vaultId, e);
        }
    }

    private Path vaultDir(String vaultId) {
        return paths.vaultsDir().resolve(vaultId);
    }

    private Vault readVault(Path file) {
        try {
            return objectMapper.readValue(file.toFile(), Vault.class);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere il file vault " + file, e);
        }
    }
}
