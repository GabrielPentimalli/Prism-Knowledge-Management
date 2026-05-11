package it.uniroma3.sii.model;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    private String docId;
    private String chunkId;
    private String text;
    private Integer pageNumber;
    private Integer paragraphIndex;
    private Integer startOffset;
    private Integer endOffset;
    private Map<String, String> metadata;
}
