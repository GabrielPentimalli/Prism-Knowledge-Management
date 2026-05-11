package it.uniroma3.sii.service;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import it.uniroma3.sii.dto.FileIndexStatus;
import it.uniroma3.sii.model.DocumentChunk;
import it.uniroma3.sii.model.DocumentPage;
import it.uniroma3.sii.model.DocumentStatus;
import it.uniroma3.sii.model.DocumentType;
import it.uniroma3.sii.model.KnowledgeDocument;
import it.uniroma3.sii.service.indexing.ChunkIndexService;
import it.uniroma3.sii.service.indexing.DocumentChunkerService;
import it.uniroma3.sii.service.indexing.DocumentExtractionService;
import it.uniroma3.sii.service.indexing.ParsedParagraph;
import it.uniroma3.sii.service.storage.DocumentRepository;
import it.uniroma3.sii.service.storage.PrivacyLogService;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentExtractionService extractionService;
    private final DocumentChunkerService chunkerService;
    private final ChunkIndexService chunkIndexService;
    private final PrivacyLogService privacyLogService;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentExtractionService extractionService,
            DocumentChunkerService chunkerService,
            ChunkIndexService chunkIndexService,
            PrivacyLogService privacyLogService) {
        this.documentRepository = documentRepository;
        this.extractionService = extractionService;
        this.chunkerService = chunkerService;
        this.chunkIndexService = chunkIndexService;
        this.privacyLogService = privacyLogService;
    }

    public FileIndexStatus ingest(MultipartFile file) {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        DocumentType type = resolveType(fileName);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile leggere il file caricato: " + fileName, e);
        }

        String hash = sha256(bytes);
        Optional<KnowledgeDocument> existing = documentRepository.findByHash(hash);
        if (existing.isPresent()) {
            KnowledgeDocument doc = existing.get();
            privacyLogService.log("Riutilizzato indice documento invariato: " + fileName + " -> " + doc.getId());
            return FileIndexStatus.builder()
                    .fileName(fileName)
                    .documentId(doc.getId())
                    .status(DocumentStatus.INDEXED)
                    .message("Documento già indicizzato (hash invariato)")
                    .build();
        }

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id(UUID.randomUUID().toString())
                .name(fileName)
                .type(type)
                .hash(hash)
                .size(bytes.length)
                .createdAt(LocalDateTime.now())
                .status(DocumentStatus.INDEXING)
                .build();

        documentRepository.saveMetadata(document);

        try {
            String extension = type == DocumentType.PDF ? "pdf" : "html";
            var sourcePath = documentRepository.saveSource(document.getId(), extension, new ByteArrayInputStream(bytes));
            document.setPath(sourcePath.toString());
            documentRepository.saveMetadata(document);

            List<ParsedParagraph> paragraphs = extractionService.extract(sourcePath, type);
            List<DocumentChunk> chunks = chunkerService.chunk(document, paragraphs);
            documentRepository.saveChunks(document.getId(), chunks);
            chunkIndexService.indexDocumentChunks(document.getId(), document.getName(), document.getType().name(), chunks);

            List<DocumentPage> pages = extractionService.extractPages(sourcePath, type);
            documentRepository.savePages(document.getId(), pages);

            document.setStatus(DocumentStatus.INDEXED);
            document.setIndexedAt(LocalDateTime.now());
            document.setError(null);
            documentRepository.saveMetadata(document);
            privacyLogService.log("Indicizzato documento locale: " + fileName + " (" + document.getId() + ")");

            return FileIndexStatus.builder()
                    .fileName(fileName)
                    .documentId(document.getId())
                    .status(DocumentStatus.INDEXED)
                    .message("Indicizzazione completata")
                    .build();
        } catch (Exception e) {
            document.setStatus(DocumentStatus.ERROR);
            document.setError(e.getMessage());
            documentRepository.saveMetadata(document);
            privacyLogService.log("Errore indicizzazione documento locale: " + fileName + " -> " + e.getMessage());
            return FileIndexStatus.builder()
                    .fileName(fileName)
                    .documentId(document.getId())
                    .status(DocumentStatus.ERROR)
                    .message(e.getMessage())
                    .build();
        }
    }

    public List<KnowledgeDocument> listDocuments() {
        return documentRepository.listDocuments();
    }

    public Optional<KnowledgeDocument> findDocument(String id) {
        return documentRepository.findById(id);
    }

    public List<DocumentChunk> readChunks(String id) {
        return documentRepository.readChunks(id);
    }

    public List<DocumentPage> readPages(String id) {
        List<DocumentPage> cached = documentRepository.readPages(id);
        if (!cached.isEmpty()) {
            return cached;
        }
        Optional<KnowledgeDocument> doc = documentRepository.findById(id);
        Optional<java.nio.file.Path> source = documentRepository.sourcePath(id);
        if (doc.isEmpty() || source.isEmpty()) {
            return List.of();
        }
        List<DocumentPage> pages = extractionService.extractPages(source.get(), doc.get().getType());
        documentRepository.savePages(id, pages);
        return pages;
    }

    public Optional<java.nio.file.Path> sourcePath(String id) {
        return documentRepository.sourcePath(id);
    }

    public void deleteDocument(String docId) {
        chunkIndexService.deleteDocument(docId);
        documentRepository.deleteDocument(docId);
        privacyLogService.log("Eliminato documento locale: " + docId);
    }

    private DocumentType resolveType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return DocumentType.PDF;
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return DocumentType.HTML;
        }
        throw new IllegalArgumentException("Formato non supportato. Consentiti solo PDF o HTML.");
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile calcolare hash SHA-256", e);
        }
    }
}
