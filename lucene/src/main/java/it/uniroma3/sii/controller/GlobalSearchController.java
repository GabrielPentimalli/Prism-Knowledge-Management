package it.uniroma3.sii.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.uniroma3.sii.dto.GlobalSearchResponse;
import it.uniroma3.sii.model.DocumentType;
import it.uniroma3.sii.service.Searcher;

@RestController
@RequestMapping("/api/search")
public class GlobalSearchController {

    private final Searcher searcher;

    public GlobalSearchController(Searcher searcher) {
        this.searcher = searcher;
    }

    @GetMapping("/global")
    public ResponseEntity<GlobalSearchResponse> globalSearch(
            @RequestParam("q") String q,
            @RequestParam(name = "fileType", required = false) DocumentType fileType,
            @RequestParam(name = "vaultId", required = false) String vaultId,
            @RequestParam(name = "fileName", required = false) String fileName,
            @RequestParam(name = "minSizeKb", required = false) Long minSizeKb,
            @RequestParam(name = "maxSizeKb", required = false) Long maxSizeKb) {
        return ResponseEntity.ok(searcher.globalSearch(q, fileType, vaultId, fileName, minSizeKb, maxSizeKb));
    }
}
