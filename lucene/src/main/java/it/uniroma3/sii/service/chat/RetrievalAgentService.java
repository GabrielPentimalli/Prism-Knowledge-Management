package it.uniroma3.sii.service.chat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.uniroma3.sii.model.ChatScopeType;
import it.uniroma3.sii.model.Vault;
import it.uniroma3.sii.service.VaultService;
import it.uniroma3.sii.service.indexing.ChunkIndexService;

@Service
public class RetrievalAgentService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalAgentService.class);

    private final ChunkIndexService chunkIndexService;
    private final VaultService vaultService;
    private final int topK;
    private final int minPerDoc;
    private final int vaultPerDocCap;

    public RetrievalAgentService(
            ChunkIndexService chunkIndexService,
            VaultService vaultService,
            @Value("${prism.retrieval.top-k}") int topK,
            @Value("${prism.retrieval.min-chunks-per-doc:2}") int minPerDoc,
            @Value("${prism.retrieval.vault-per-doc-cap:3}") int vaultPerDocCap) {
        this.chunkIndexService = chunkIndexService;
        this.vaultService = vaultService;
        this.topK = topK;
        this.minPerDoc = Math.max(1, minPerDoc);
        this.vaultPerDocCap = Math.max(minPerDoc, vaultPerDocCap);
    }

    public List<ChunkIndexService.ChunkHit> retrieve(ChatScopeType scopeType, String scopeId, String userMessage) {
        Set<String> docIds = resolveDocIds(scopeType, scopeId);
        if (docIds.isEmpty()) {
            log.warn("Retrieval con scope {} {} senza documenti.", scopeType, scopeId);
            return List.of();
        }

        int effectiveTopK = scopeType == ChatScopeType.VAULT
                ? Math.max(topK, docIds.size() * vaultPerDocCap)
                : topK;
        List<ChunkIndexService.ChunkHit> textHits = chunkIndexService.search(userMessage, docIds, effectiveTopK);
        log.debug("Retrieval {} scope={} docIds={} testo-match={}",
                scopeType, scopeId, docIds.size(), textHits.size());

        if (scopeType == ChatScopeType.DOCUMENT) {
            return ensureMinimum(textHits, docIds);
        }

        return balanceAcrossVault(textHits, docIds);
    }

    private List<ChunkIndexService.ChunkHit> balanceAcrossVault(
            List<ChunkIndexService.ChunkHit> textHits,
            Set<String> docIds) {

        Map<String, Long> countPerDoc = textHits.stream()
                .collect(Collectors.groupingBy(ChunkIndexService.ChunkHit::docId, Collectors.counting()));

        Set<String> underrepresented = docIds.stream()
                .filter(id -> countPerDoc.getOrDefault(id, 0L) < minPerDoc)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (underrepresented.isEmpty()) {
            return textHits;
        }

        log.debug("Vault retrieval: documenti sotto-rappresentati = {}", underrepresented);
        List<ChunkIndexService.ChunkHit> sample =
                chunkIndexService.firstChunksPerDoc(underrepresented, minPerDoc);

        Set<String> seen = textHits.stream()
                .map(ChunkIndexService.ChunkHit::chunkId)
                .collect(Collectors.toCollection(HashSet::new));

        List<ChunkIndexService.ChunkHit> combined = new ArrayList<>(textHits);
        for (ChunkIndexService.ChunkHit hit : sample) {
            if (seen.add(hit.chunkId())) {
                combined.add(hit);
            }
        }
        log.debug("Vault retrieval finale: {} chunk ({} testo + {} sampling)",
                combined.size(), textHits.size(), combined.size() - textHits.size());
        return combined;
    }

    private List<ChunkIndexService.ChunkHit> ensureMinimum(
            List<ChunkIndexService.ChunkHit> hits,
            Set<String> docIds) {
        if (!hits.isEmpty()) {
            return hits;
        }
        log.debug("Document retrieval: nessun match testuale, sampling primi chunk.");
        return chunkIndexService.firstChunksPerDoc(docIds, minPerDoc);
    }

    private Set<String> resolveDocIds(ChatScopeType scopeType, String scopeId) {
        if (scopeType == ChatScopeType.DOCUMENT) {
            return Set.of(scopeId);
        }
        Vault vault = vaultService.findById(scopeId)
                .orElseThrow(() -> new IllegalArgumentException("Vault non trovato: " + scopeId));
        return vault.getDocIds();
    }
}
