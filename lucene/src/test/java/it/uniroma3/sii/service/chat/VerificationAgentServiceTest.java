package it.uniroma3.sii.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import it.uniroma3.sii.model.Citation;
import it.uniroma3.sii.service.indexing.ChunkIndexService.ChunkHit;

class VerificationAgentServiceTest {

    private VerificationAgentService service;

    @BeforeEach
    void setUp() {
        service = new VerificationAgentService();
    }

    private ChunkHit hit(String chunkId) {
        return new ChunkHit("doc1", "Documento 1", "PDF", chunkId, "testo", 3, 2, 0.7f);
    }

    private StructuredAnswer answerWith(String text, Citation... citations) {
        StructuredAnswer a = new StructuredAnswer();
        a.setAnswer(text);
        a.setCitations(List.of(citations));
        return a;
    }

    @Test
    void validCitationIsKeptAndEnrichedFromRetrieval() {
        StructuredAnswer candidate = answerWith("risposta",
                Citation.builder().chunkId("c-1").build());

        StructuredAnswer r = service.verify(candidate, List.of(hit("c-1")));

        assertThat(r.getAnswer()).isEqualTo("risposta");
        assertThat(r.getCitations()).hasSize(1);
        Citation c = r.getCitations().get(0);
        assertThat(c.getDocName()).isEqualTo("Documento 1");
        assertThat(c.getPage()).isEqualTo(3);
        assertThat(c.getParagraphIndex()).isEqualTo(2);
    }

    @Test
    void answerWithoutCitationsIsKept_notDowngradedToInsufficient() {
        StructuredAnswer candidate = answerWith("risposta fondata sui frammenti");

        StructuredAnswer r = service.verify(candidate, List.of(hit("c-1")));

        assertThat(r.getAnswer()).isEqualTo("risposta fondata sui frammenti");
        assertThat(r.getCitations()).isEmpty();
    }

    @Test
    void citationWithUnknownChunkIdIsDropped_butAnswerSurvives() {
        StructuredAnswer candidate = answerWith("risposta",
                Citation.builder().chunkId("inesistente").build());

        StructuredAnswer r = service.verify(candidate, List.of(hit("c-1")));

        assertThat(r.getAnswer()).isEqualTo("risposta");
        assertThat(r.getCitations()).isEmpty();
    }

    @Test
    void explicitRefusalStaysInsufficient() {
        StructuredAnswer candidate = answerWith(
                "non ho evidenza sufficiente nei documenti caricati");

        StructuredAnswer r = service.verify(candidate, List.of(hit("c-1")));

        assertThat(r.getAnswer()).isEqualTo(SynthesisAgentService.INSUFFICIENT);
        assertThat(r.getCitations()).isEmpty();
    }

    @Test
    void nullCandidateIsInsufficient() {
        StructuredAnswer r = service.verify(null, List.of(hit("c-1")));

        assertThat(r.getAnswer()).isEqualTo(SynthesisAgentService.INSUFFICIENT);
    }

    @Test
    void blankAnswerIsInsufficient() {
        StructuredAnswer r = service.verify(answerWith("  "), List.of(hit("c-1")));

        assertThat(r.getAnswer()).isEqualTo(SynthesisAgentService.INSUFFICIENT);
    }
}
