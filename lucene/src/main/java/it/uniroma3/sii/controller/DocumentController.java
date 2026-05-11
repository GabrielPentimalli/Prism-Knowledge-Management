package it.uniroma3.sii.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import it.uniroma3.sii.dto.DocumentUploadResponse;
import it.uniroma3.sii.dto.FileIndexStatus;
import it.uniroma3.sii.dto.KnowledgeDocumentResponse;
import it.uniroma3.sii.model.DocumentPage;
import it.uniroma3.sii.model.KnowledgeDocument;
import it.uniroma3.sii.service.DocumentService;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        FileIndexStatus status = documentService.ingest(file);
        return ResponseEntity.ok(DocumentUploadResponse.builder().files(List.of(status)).build());
    }

    @GetMapping
    public ResponseEntity<List<KnowledgeDocument>> list() {
        return ResponseEntity.ok(documentService.listDocuments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeDocumentResponse> get(@PathVariable String id) {
        KnowledgeDocument document = documentService.findDocument(id)
                .orElseThrow(() -> new IllegalArgumentException("Documento non trovato: " + id));
        return ResponseEntity.ok(KnowledgeDocumentResponse.builder()
                .document(document)
                .chunks(documentService.readChunks(id))
                .build());
    }

    @GetMapping("/{id}/pages")
    public ResponseEntity<List<DocumentPage>> pages(@PathVariable String id) {
        return ResponseEntity.ok(documentService.readPages(id));
    }

    @GetMapping("/{id}/source")
    public ResponseEntity<ByteArrayResource> source(@PathVariable String id) throws IOException {
        var sourcePath = documentService.sourcePath(id)
                .orElseThrow(() -> new IllegalArgumentException("Sorgente documento non trovata: " + id));
        byte[] bytes = Files.readAllBytes(sourcePath);
        String filename = sourcePath.getFileName().toString();
        MediaType mediaType = resolveMediaType(filename);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(new ByteArrayResource(bytes));
    }

    private MediaType resolveMediaType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return MediaType.TEXT_HTML;
        if (lower.endsWith(".docx")) {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (lower.endsWith(".xml")) return MediaType.APPLICATION_XML;
        return MediaType.TEXT_PLAIN;
    }
}
