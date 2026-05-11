package it.uniroma3.sii.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import it.uniroma3.sii.dto.CreateVaultRequest;
import it.uniroma3.sii.dto.DocumentUploadResponse;
import it.uniroma3.sii.dto.FileIndexStatus;
import it.uniroma3.sii.dto.VaultResponse;
import it.uniroma3.sii.model.Vault;
import it.uniroma3.sii.service.VaultService;

@RestController
@RequestMapping("/api/vaults")
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    @PostMapping
    public ResponseEntity<Vault> create(@RequestBody CreateVaultRequest request) {
        return ResponseEntity.ok(vaultService.create(request.getName()));
    }

    @GetMapping
    public ResponseEntity<List<Vault>> list() {
        return ResponseEntity.ok(vaultService.listVaults());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VaultResponse> get(@PathVariable String id) {
        Vault vault = vaultService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vault non trovato: " + id));
        return ResponseEntity.ok(VaultResponse.builder()
                .vault(vault)
                .documents(vaultService.resolveDocuments(vault))
                .build());
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> addDocuments(
            @PathVariable String id,
            @RequestParam("files") MultipartFile[] files) {
        List<FileIndexStatus> statuses = vaultService.addDocuments(id, files);
        return ResponseEntity.ok(DocumentUploadResponse.builder().files(statuses).build());
    }

    @DeleteMapping("/{id}/documents/{docId}")
    public ResponseEntity<Void> removeDocument(@PathVariable String id, @PathVariable String docId) {
        vaultService.removeDocument(id, docId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVault(@PathVariable String id) {
        vaultService.deleteVault(id);
        return ResponseEntity.noContent().build();
    }
}
