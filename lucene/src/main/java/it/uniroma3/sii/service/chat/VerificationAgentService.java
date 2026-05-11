package it.uniroma3.sii.service.chat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import it.uniroma3.sii.model.Citation;
import it.uniroma3.sii.service.indexing.ChunkIndexService;

@Service
public class VerificationAgentService {

    private static final Logger log = LoggerFactory.getLogger(VerificationAgentService.class);

    public StructuredAnswer verify(StructuredAnswer candidate, java.util.List<ChunkIndexService.ChunkHit> retrievalHits) {
        if (candidate == null) {
            log.debug("Verification: candidato nullo -> insufficient.");
            return insufficient();
        }
        if (candidate.getAnswer() == null || candidate.getAnswer().isBlank()) {
            log.debug("Verification: answer vuoto -> insufficient.");
            return insufficient();
        }

        Map<String, ChunkIndexService.ChunkHit> byChunkId = retrievalHits.stream()
                .collect(Collectors.toMap(ChunkIndexService.ChunkHit::chunkId, h -> h, (a, b) -> a));
        Set<String> validIds = byChunkId.keySet();

        if (candidate.getCitations() == null || candidate.getCitations().isEmpty()) {
            log.debug("Verification: nessuna citation prodotta -> insufficient.");
            return insufficient();
        }

        java.util.List<Citation> verified = candidate.getCitations().stream()
                .filter(c -> c.getChunkId() != null && validIds.contains(c.getChunkId()))
                .map(c -> enrich(c, byChunkId.get(c.getChunkId())))
                .toList();

        if (verified.isEmpty()) {
            log.debug("Verification: tutte le citation rifiutate (chunkId non in retrieval). Proposte: {}",
                    candidate.getCitations().stream().map(Citation::getChunkId).toList());
            return insufficient();
        }
        log.debug("Verification: {} citation valide su {} proposte.", verified.size(), candidate.getCitations().size());
        candidate.setCitations(verified);
        return candidate;
    }

    private Citation enrich(Citation citation, ChunkIndexService.ChunkHit hit) {
        citation.setDocId(hit.docId());
        citation.setDocName(hit.docName());
        citation.setPage(hit.page());
        citation.setParagraphIndex(hit.paragraphIndex());
        citation.setScore((double) hit.score());
        return citation;
    }

    private StructuredAnswer insufficient() {
        StructuredAnswer answer = new StructuredAnswer();
        answer.setAnswer("non ho evidenza sufficiente nei documenti caricati");
        answer.setCitations(java.util.List.of());
        return answer;
    }
}
