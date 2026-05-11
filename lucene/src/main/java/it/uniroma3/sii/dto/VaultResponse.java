package it.uniroma3.sii.dto;

import java.util.List;

import it.uniroma3.sii.model.KnowledgeDocument;
import it.uniroma3.sii.model.Vault;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultResponse {
    private Vault vault;
    private List<KnowledgeDocument> documents;
}
