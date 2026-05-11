package it.uniroma3.sii.dto;

import it.uniroma3.sii.model.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchHit {
    private String docId;
    private String docName;
    private DocumentType fileType;
    private String vaultId;
    private String chunkId;
    private Integer page;
    private Integer paragraphIndex;
    private String snippet;
    private float score;
}
