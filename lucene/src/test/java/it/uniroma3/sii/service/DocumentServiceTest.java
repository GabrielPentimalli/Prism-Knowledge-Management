package it.uniroma3.sii.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockMultipartFile;

import it.uniroma3.sii.model.DocumentStatus;
import it.uniroma3.sii.model.DocumentType;
import it.uniroma3.sii.model.KnowledgeDocument;
import it.uniroma3.sii.service.indexing.ChunkIndexService;
import it.uniroma3.sii.service.indexing.DocumentChunkerService;
import it.uniroma3.sii.service.indexing.DocumentExtractionService;
import it.uniroma3.sii.service.storage.DocumentRepository;
import it.uniroma3.sii.service.storage.PrivacyLogService;

class DocumentServiceTest {

    private DocumentRepository repository;
    private DocumentExtractionService extraction;
    private ChunkIndexService index;
    private DocumentService service;
    private KnowledgeDocument existing;
    private final MockMultipartFile upload = new MockMultipartFile("file", "document.txt", "text/plain", "text".getBytes());

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        extraction = mock(DocumentExtractionService.class);
        index = mock(ChunkIndexService.class);
        service = new DocumentService(repository, extraction, mock(DocumentChunkerService.class),
                index, mock(PrivacyLogService.class));
        existing = KnowledgeDocument.builder().id("doc-1").name("document.txt").type(DocumentType.TXT)
                .createdAt(LocalDateTime.now()).error("previous failure").build();
        when(repository.findByHash(anyString())).thenReturn(Optional.of(existing));
        when(repository.saveSource(eq("doc-1"), eq("txt"), any())).thenReturn(Path.of("source.txt"));
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = {"ERROR", "INDEXING", "UPLOADED"})
    void unfinishedDocumentsAreReindexedWithTheSameId(DocumentStatus status) {
        existing.setStatus(status);

        var result = service.ingest(upload);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(result.getDocumentId()).isEqualTo("doc-1");
        assertThat(existing.getError()).isNull();
        verify(extraction).extract(Path.of("source.txt"), DocumentType.TXT);
        verify(index).indexDocumentChunks(eq("doc-1"), eq("document.txt"), eq("TXT"), any());
    }

    @Test
    void completedDocumentsAreReusedWithoutReindexing() {
        existing.setStatus(DocumentStatus.INDEXED);

        var result = service.ingest(upload);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        verify(repository, never()).saveMetadata(any());
        verify(extraction, never()).extract(any(), any());
    }

    @Test
    void failedRetryReportsTheActualError() {
        existing.setStatus(DocumentStatus.ERROR);
        when(extraction.extract(any(), any())).thenThrow(new IllegalStateException("invalid source"));

        var result = service.ingest(upload);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.ERROR);
        assertThat(result.getMessage()).isEqualTo("invalid source");
        assertThat(existing.getStatus()).isEqualTo(DocumentStatus.ERROR);
        verify(index, never()).indexDocumentChunks(any(), any(), any(), any());
    }
}
