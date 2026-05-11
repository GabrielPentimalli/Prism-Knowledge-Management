package it.uniroma3.sii.dto;

import it.uniroma3.sii.model.ChatScopeType;
import lombok.Data;

@Data
public class DataDeleteRequest {
    private String mode;
    private String documentId;
    private String vaultId;
    private ChatScopeType scopeType;
    private String scopeId;
}
