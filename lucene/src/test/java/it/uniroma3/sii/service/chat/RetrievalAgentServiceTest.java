package it.uniroma3.sii.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import it.uniroma3.sii.model.ChatScopeType;
import it.uniroma3.sii.model.Vault;
import it.uniroma3.sii.service.VaultService;
import it.uniroma3.sii.service.indexing.ChunkIndexService;
import it.uniroma3.sii.service.indexing.ChunkIndexService.ChunkHit;

class RetrievalAgentServiceTest {

    private ChunkHit hit(String docId, int index) {
        return new ChunkHit(docId, docId, "TXT", docId + "-" + index, "text", 1, index, 1.0f);
    }

    @Test
    void vaultResultsRespectThePerDocumentCapEvenWhenAllDocumentsMeetTheMinimum() {
        ChunkIndexService index = mock(ChunkIndexService.class);
        VaultService vaults = mock(VaultService.class);
        Set<String> ids = Set.of("A", "B");
        when(vaults.findById("vault")).thenReturn(Optional.of(Vault.builder().docIds(ids).build()));
        when(index.search("query", ids, 8)).thenReturn(List.of(hit("A", 1), hit("A", 2), hit("A", 3),
                hit("A", 4), hit("A", 5), hit("A", 6), hit("B", 1), hit("B", 2)));

        var result = new RetrievalAgentService(index, vaults, 8, 2, 3)
                .retrieve(ChatScopeType.VAULT, "vault", "query");

        assertThat(result).extracting(ChunkHit::chunkId).containsExactly("A-1", "A-2", "A-3", "B-1", "B-2");
    }

    @Test
    void samplingOnlyFillsTheMissingMinimumWithoutAddingDuplicates() {
        ChunkIndexService index = mock(ChunkIndexService.class);
        VaultService vaults = mock(VaultService.class);
        Set<String> ids = Set.of("A", "B");
        when(vaults.findById("vault")).thenReturn(Optional.of(Vault.builder().docIds(ids).build()));
        when(index.search("query", ids, 8)).thenReturn(List.of(hit("A", 1), hit("A", 2), hit("A", 3), hit("B", 3)));
        when(index.firstChunksPerDoc(Set.of("B"), 2)).thenReturn(List.of(hit("B", 1), hit("B", 2)));

        var result = new RetrievalAgentService(index, vaults, 8, 2, 3)
                .retrieve(ChatScopeType.VAULT, "vault", "query");

        assertThat(result).extracting(ChunkHit::chunkId).containsExactly("A-1", "A-2", "A-3", "B-3", "B-1");
    }
}
