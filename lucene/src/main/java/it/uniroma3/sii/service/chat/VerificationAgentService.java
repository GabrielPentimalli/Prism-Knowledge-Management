package it.uniroma3.sii.service.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        // Rifiuto onesto del modello: lo lasciamo passare così com'è.
        if (candidate.getAnswer().strip().toLowerCase()
                .contains(SynthesisAgentService.INSUFFICIENT)) {
            return insufficient();
        }

        Map<String, ChunkIndexService.ChunkHit> byChunkId = retrievalHits.stream()
                .collect(Collectors.toMap(ChunkIndexService.ChunkHit::chunkId, h -> h, (a, b) -> a));
        Set<String> validIds = byChunkId.keySet();

        // Difesa in profondità: teniamo solo le citazioni che puntano a chunk
        // realmente recuperati e le ri-arricchiamo dalla fonte autorevole.
        List<Citation> enriched = candidate.getCitations() == null
                ? List.of()
                : candidate.getCitations().stream()
                        .filter(c -> c.getChunkId() != null && validIds.contains(c.getChunkId()))
                        .map(c -> enrich(c, byChunkId.get(c.getChunkId())))
                        .toList();

        // L'utente vede le citazioni come "documento · pagina": due chunk diversi
        // della stessa pagina dello stesso documento appaiono identici. Le
        // collassiamo qui, tenendo la citazione col punteggio più alto, così non
        // mostriamo mai due voci duplicate.
        List<Citation> verified = dedupeByDocAndPage(enriched);

        // La risposta è generata SOLO dai frammenti recuperati: anche senza
        // citazioni esplicite resta fondata, quindi la restituiamo comunque
        // invece di scartarla con un falso "non ho evidenza".
        if (verified.isEmpty()) {
            log.debug("Verification: risposta valida senza citazioni verificate; restituita con citations vuote.");
        } else {
            log.debug("Verification: {} citazioni valide su {} proposte.",
                    verified.size(),
                    candidate.getCitations() == null ? 0 : candidate.getCitations().size());
        }
        candidate.setCitations(verified);
        return candidate;
    }

    // Collassa le citazioni che puntano allo stesso documento e pagina,
    // mantenendo per ciascuna coppia la citazione col punteggio migliore e
    // preservando l'ordine di prima comparsa.
    private List<Citation> dedupeByDocAndPage(List<Citation> citations) {
        LinkedHashMap<String, Citation> byDocPage = new LinkedHashMap<>();
        for (Citation c : citations) {
            String key = c.getDocId() + "#" + c.getPage();
            Citation existing = byDocPage.get(key);
            if (existing == null || scoreOf(c) > scoreOf(existing)) {
                byDocPage.put(key, c);
            }
        }
        return new ArrayList<>(byDocPage.values());
    }

    private double scoreOf(Citation c) {
        return c.getScore() == null ? Double.NEGATIVE_INFINITY : c.getScore();
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
        answer.setAnswer(SynthesisAgentService.INSUFFICIENT);
        answer.setCitations(java.util.List.of());
        return answer;
    }
}
