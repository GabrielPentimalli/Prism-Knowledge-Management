package it.uniroma3.sii.dto;

import java.util.List;

import it.uniroma3.sii.model.DocumentChunk;
import it.uniroma3.sii.model.KnowledgeDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentResponse {
    private KnowledgeDocument document;
    private List<DocumentChunk> chunks;
}
