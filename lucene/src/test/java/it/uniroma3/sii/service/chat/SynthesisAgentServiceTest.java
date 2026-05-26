package it.uniroma3.sii.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.uniroma3.sii.service.indexing.ChunkIndexService.ChunkHit;

class SynthesisAgentServiceTest {

    private LlmClient llm;
    private SynthesisAgentService service;

    @BeforeEach
    void setUp() {
        llm = mock(LlmClient.class);
        service = new SynthesisAgentService(llm, new ObjectMapper());
    }

    private ChunkHit hit(String docId, String chunkId, String text) {
        return new ChunkHit(docId, "Doc " + docId, "PDF", chunkId, text, 1, 0, 1.0f);
    }

    private List<ChunkHit> twoChunks() {
        return List.of(hit("A", "A-0", "primo frammento"), hit("B", "B-0", "secondo frammento"));
    }

    private void llmReturns(String raw) {
        when(llm.chatStructured(anyString(), anyString(), anyString(), anyMap())).thenReturn(raw);
    }

    @Test
    void validJsonWithSources_mapsIntegerIndicesToRealCitations() {
        llmReturns("{\"answer\":\"risposta valida\",\"sources\":[1,2]}");

        StructuredAnswer r = service.synthesize("domanda", twoChunks());

        assertThat(r.getAnswer()).isEqualTo("risposta valida");
        assertThat(r.getCitations()).hasSize(2);
        assertThat(r.getCitations().get(0).getChunkId()).isEqualTo("A-0");
        assertThat(r.getCitations().get(1).getChunkId()).isEqualTo("B-0");
    }

    @Test
    void proseAnswerNotJson_isReturnedGracefullyInsteadOfInsufficient() {
        llmReturns("Questa è una risposta in prosa, senza alcun JSON.");

        StructuredAnswer r = service.synthesize("domanda", twoChunks());

        assertThat(r.getAnswer()).contains("prosa");
        assertThat(r.getCitations()).isEmpty();
    }

    @Test
    void jsonWithoutRecognizableAnswerKey_returnsInsufficientWithoutSurfacingJunk() {
        // Schema-drift tipico dei modelli piccoli: nessun campo risposta.
        llmReturns("{\"title\":\"qualcosa\",\"citation_count\":0,\"doi\":null}");

        StructuredAnswer r = service.synthesize("domanda", twoChunks());

        assertThat(r.getAnswer()).isEqualTo(SynthesisAgentService.INSUFFICIENT);
        assertThat(r.getCitations()).isEmpty();
    }

    @Test
    void explicitRefusal_returnsInsufficient() {
        llmReturns("{\"answer\":\"non ho evidenza sufficiente nei documenti caricati\",\"sources\":[]}");

        StructuredAnswer r = service.synthesize("domanda", twoChunks());

        assertThat(r.getAnswer()).isEqualTo(SynthesisAgentService.INSUFFICIENT);
        assertThat(r.getCitations()).isEmpty();
    }

    @Test
    void tolerantToAlternativeAnswerKey() {
        llmReturns("{\"response\":\"ciao\",\"sources\":[1]}");

        StructuredAnswer r = service.synthesize("domanda", twoChunks());

        assertThat(r.getAnswer()).isEqualTo("ciao");
        assertThat(r.getCitations()).hasSize(1);
    }

    @Test
    void outOfRangeAndDuplicateSourceIndicesAreIgnored() {
        llmReturns("{\"answer\":\"x\",\"sources\":[1,1,0,5,-2]}");

        StructuredAnswer r = service.synthesize("domanda", twoChunks());

        assertThat(r.getCitations()).hasSize(1);
        assertThat(r.getCitations().get(0).getChunkId()).isEqualTo("A-0");
    }

    @Test
    void emptyChunks_returnsInsufficientWithoutCallingLlm() {
        StructuredAnswer r = service.synthesize("domanda", List.of());

        assertThat(r.getAnswer()).isEqualTo(SynthesisAgentService.INSUFFICIENT);
        verify(llm, never()).chatStructured(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void blankLlmOutput_returnsInsufficient() {
        llmReturns("   ");

        StructuredAnswer r = service.synthesize("domanda", twoChunks());

        assertThat(r.getAnswer()).isEqualTo(SynthesisAgentService.INSUFFICIENT);
    }
}
