package it.uniroma3.sii.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import it.uniroma3.sii.dto.GlobalSearchHit;
import it.uniroma3.sii.dto.GlobalSearchResponse;
import it.uniroma3.sii.model.DocumentType;
import it.uniroma3.sii.model.KnowledgeDocument;
import it.uniroma3.sii.model.Vault;
import it.uniroma3.sii.service.indexing.ChunkIndexService;

@Service
public class Searcher {

    private final ChunkIndexService chunkIndexService;
    private final DocumentService documentService;
    private final VaultService vaultService;

    public Searcher(
            ChunkIndexService chunkIndexService,
            DocumentService documentService,
            VaultService vaultService) {
        this.chunkIndexService = chunkIndexService;
        this.documentService = documentService;
        this.vaultService = vaultService;
    }

    public GlobalSearchResponse globalSearch(
            String query,
            DocumentType fileType,
            String vaultId,
            LocalDate addedAfter,
            LocalDate addedBefore) {

        List<KnowledgeDocument> documents = resolveDocuments(vaultId).stream()
                .filter(doc -> fileType == null || doc.getType() == fileType)
                .filter(doc -> afterDate(doc, addedAfter))
                .filter(doc -> beforeDate(doc, addedBefore))
                .toList();

        if (documents.isEmpty()) {
            return GlobalSearchResponse.builder().hits(List.of()).total(0).build();
        }

        Set<String> docIds = documents.stream().map(KnowledgeDocument::getId).collect(Collectors.toSet());
        var hits = chunkIndexService.search(query, docIds, 50);
        HashMap<String, String> docToVault = buildDocToVaultMap();
        List<GlobalSearchHit> mapped = hits.stream().map(hit -> GlobalSearchHit.builder()
                .docId(hit.docId())
                .docName(hit.docName())
                .fileType(DocumentType.valueOf(hit.fileType()))
                .vaultId(docToVault.get(hit.docId()))
                .chunkId(hit.chunkId())
                .page(hit.page())
                .paragraphIndex(hit.paragraphIndex())
                .snippet(truncate(hit.text(), 220))
                .score(hit.score())
                .build()).toList();
        return GlobalSearchResponse.builder().hits(mapped).total(mapped.size()).build();
    }

    private List<KnowledgeDocument> resolveDocuments(String vaultId) {
        if (vaultId == null || vaultId.isBlank()) {
            return documentService.listDocuments();
        }
        Optional<Vault> vault = vaultService.findById(vaultId);
        if (vault.isEmpty()) {
            return List.of();
        }
        return vaultService.resolveDocuments(vault.get());
    }

    private HashMap<String, String> buildDocToVaultMap() {
        HashMap<String, String> mapping = new HashMap<>();
        for (Vault vault : vaultService.listVaults()) {
            for (String docId : vault.getDocIds()) {
                mapping.put(docId, vault.getId());
            }
        }
        return mapping;
    }

    private boolean afterDate(KnowledgeDocument doc, LocalDate addedAfter) {
        if (addedAfter == null || doc.getCreatedAt() == null) {
            return true;
        }
        return !doc.getCreatedAt().toLocalDate().isBefore(addedAfter);
    }

    private boolean beforeDate(KnowledgeDocument doc, LocalDate addedBefore) {
        if (addedBefore == null || doc.getCreatedAt() == null) {
            return true;
        }
        return !doc.getCreatedAt().toLocalDate().isAfter(addedBefore);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }
}
