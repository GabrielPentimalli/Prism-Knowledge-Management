package it.uniroma3.sii.dto;

import it.uniroma3.sii.model.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileIndexStatus {
    private String fileName;
    private String documentId;
    private DocumentStatus status;
    private String message;
}
