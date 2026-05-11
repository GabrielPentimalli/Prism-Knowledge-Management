package it.uniroma3.sii.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.uniroma3.sii.model.DocumentChunk;
import it.uniroma3.sii.model.DocumentPage;
import it.uniroma3.sii.model.KnowledgeDocument;

@Repository
public class DocumentRepository {

    private static final String METADATA_FILE = "document.json";
    private static final String CHUNKS_FILE = "chunks.json";
    private static final String PAGES_FILE = "pages.json";

    private final StoragePathService paths;
    private final ObjectMapper objectMapper;

    public DocumentRepository(StoragePathService paths, ObjectMapper objectMapper) {
        this.paths = paths;
        this.objectMapper = objectMapper;
    }

    public Path saveSource(String docId, String extension, InputStream source) {
        try {
            paths.ensureBaseDirectories();
            Path docDir = documentDir(docId);
            Files.createDirectories(docDir);
            Path destination = docDir.resolve("source." + extension);
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile salvare il file sorgente locale", e);
        }
    }

    public void saveMetadata(KnowledgeDocument document) {
        try {
            Path docDir = documentDir(document.getId());
            Files.createDirectories(docDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(docDir.resolve(METADATA_FILE).toFile(), document);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile salvare i metadati del documento " + document.getId(), e);
        }
    }

    public void saveChunks(String docId, List<DocumentChunk> chunks) {
        try {
            Path docDir = documentDir(docId);
            Files.createDirectories(docDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(docDir.resolve(CHUNKS_FILE).toFile(), chunks);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile salvare i chunk del documento " + docId, e);
        }
    }

    public void savePages(String docId, List<DocumentPage> pages) {
        try {
            Path docDir = documentDir(docId);
            Files.createDirectories(docDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(docDir.resolve(PAGES_FILE).toFile(), pages);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile salvare le pagine del documento " + docId, e);
        }
    }

    public List<DocumentPage> readPages(String docId) {
        Path file = documentDir(docId).resolve(PAGES_FILE);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(file.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere le pagine del documento " + docId, e);
        }
    }

    public Optional<KnowledgeDocument> findById(String docId) {
        Path file = documentDir(docId).resolve(METADATA_FILE);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), KnowledgeDocument.class));
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere il documento " + docId, e);
        }
    }

    public List<KnowledgeDocument> listDocuments() {
        Path root = paths.documentsDir();
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(dir -> dir.resolve(METADATA_FILE))
                    .filter(Files::exists)
                    .map(this::readMetadataFile)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(KnowledgeDocument::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile elencare i documenti locali", e);
        }
    }

    public Optional<KnowledgeDocument> findByHash(String hash) {
        return listDocuments().stream().filter(d -> hash.equals(d.getHash())).findFirst();
    }

    public List<DocumentChunk> readChunks(String docId) {
        Path file = documentDir(docId).resolve(CHUNKS_FILE);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(file.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere i chunk del documento " + docId, e);
        }
    }

    public Optional<Path> sourcePath(String docId) {
        Path dir = documentDir(docId);
        if (!Files.exists(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().startsWith("source."))
                    .findFirst();
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile risolvere il file sorgente per documento " + docId, e);
        }
    }

    public void deleteDocument(String docId) {
        Path dir = documentDir(docId);
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
            throw new IllegalStateException("Impossibile eliminare la directory del documento " + docId, e);
        }
    }

    private Path documentDir(String docId) {
        return paths.documentsDir().resolve(docId);
    }

    private KnowledgeDocument readMetadataFile(Path metadataFile) {
        try {
            return objectMapper.readValue(metadataFile.toFile(), KnowledgeDocument.class);
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile leggere " + metadataFile, e);
        }
    }
}
