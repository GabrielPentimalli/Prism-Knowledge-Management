package it.uniroma3.sii.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {
    private String id;
    private String name;
    private DocumentType type;
    private String path;
    private String hash;
    private long size;
    private LocalDateTime createdAt;
    private LocalDateTime indexedAt;
    private DocumentStatus status;
    private String error;
}
