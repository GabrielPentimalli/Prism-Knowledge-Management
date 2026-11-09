package it.uniroma3.sii.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import it.uniroma3.sii.service.DocumentService;

class DocumentControllerTest {

    @TempDir
    Path root;

    @Test
    void activeDocumentSourcesAreIsolatedEvenWhenOpenedDirectly() throws Exception {
        DocumentService documents = mock(DocumentService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new DocumentController(documents)).build();
        String source = "<script>fetch('/api/settings')</script>";
        for (String extension : new String[] {"html", "htm", "xml"}) {
            Path file = Files.writeString(root.resolve("source." + extension), source);
            when(documents.sourcePath("doc-1")).thenReturn(Optional.of(file));

            mvc.perform(get("/api/documents/doc-1/source"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy",
                            "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data:"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(content().string(source));
        }
    }

    @Test
    void pdfSourcesKeepTheirViewerMediaType() throws Exception {
        DocumentService documents = mock(DocumentService.class);
        Path file = Files.writeString(root.resolve("source.pdf"), "%PDF");
        when(documents.sourcePath("doc-1")).thenReturn(Optional.of(file));

        MockMvcBuilders.standaloneSetup(new DocumentController(documents)).build()
                .perform(get("/api/documents/doc-1/source"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().doesNotExist("Content-Security-Policy"));
    }
}
