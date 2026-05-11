package it.uniroma3.sii.service.indexing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.uniroma3.sii.model.DocumentChunk;
import it.uniroma3.sii.model.KnowledgeDocument;

@Service
public class DocumentChunkerService {

    private final int chunkSize;
    private final int overlap;

    public DocumentChunkerService(
            @Value("${prism.chunk.size}") int chunkSize,
            @Value("${prism.chunk.overlap}") int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<DocumentChunk> chunk(KnowledgeDocument document, List<ParsedParagraph> paragraphs) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (paragraphs.isEmpty()) {
            return chunks;
        }

        List<ParsedParagraph> buffer = new ArrayList<>();
        int textLen = 0;
        int chunkIndex = 0;

        for (ParsedParagraph paragraph : paragraphs) {
            int candidateLen = textLen + paragraph.text().length() + 1;
            if (candidateLen > chunkSize && !buffer.isEmpty()) {
                chunks.add(toChunk(document, buffer, chunkIndex++));
                buffer = overlapBuffer(buffer);
                textLen = totalLength(buffer);
            }
            buffer.add(paragraph);
            textLen += paragraph.text().length() + 1;
        }

        if (!buffer.isEmpty()) {
            chunks.add(toChunk(document, buffer, chunkIndex));
        }
        return chunks;
    }

    private DocumentChunk toChunk(KnowledgeDocument document, List<ParsedParagraph> paragraphs, int index) {
        ParsedParagraph first = paragraphs.get(0);
        ParsedParagraph last = paragraphs.get(paragraphs.size() - 1);
        String text = paragraphs.stream().map(ParsedParagraph::text).reduce((a, b) -> a + "\n" + b).orElse("");
        return DocumentChunk.builder()
                .docId(document.getId())
                .chunkId(document.getId() + "-chunk-" + index)
                .text(text)
                .pageNumber(first.pageNumber())
                .paragraphIndex(first.paragraphIndex())
                .startOffset(first.startOffset())
                .endOffset(last.endOffset())
                .metadata(new HashMap<>(java.util.Map.of(
                        "docName", document.getName(),
                        "type", document.getType().name())))
                .build();
    }

    private List<ParsedParagraph> overlapBuffer(List<ParsedParagraph> current) {
        List<ParsedParagraph> overlapParagraphs = new ArrayList<>();
        int chars = 0;
        for (int i = current.size() - 1; i >= 0; i--) {
            ParsedParagraph paragraph = current.get(i);
            overlapParagraphs.add(0, paragraph);
            chars += paragraph.text().length();
            if (chars >= overlap) {
                break;
            }
        }
        return overlapParagraphs;
    }

    private int totalLength(List<ParsedParagraph> paragraphs) {
        return paragraphs.stream().mapToInt(p -> p.text().length() + 1).sum();
    }
}
