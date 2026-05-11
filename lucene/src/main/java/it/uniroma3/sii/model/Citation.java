package it.uniroma3.sii.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Citation {
    private String docId;
    private String docName;
    private Integer page;
    private Integer paragraphIndex;
    private String chunkId;
    private Double score;
}
