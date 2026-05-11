package it.uniroma3.sii.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import it.uniroma3.sii.dto.FileIndexStatus;
import it.uniroma3.sii.model.KnowledgeDocument;
import it.uniroma3.sii.model.Vault;
import it.uniroma3.sii.service.storage.PrivacyLogService;
import it.uniroma3.sii.service.storage.VaultRepository;

@Service
public class VaultService {

    private final VaultRepository vaultRepository;
    private final DocumentService documentService;
    private final PrivacyLogService privacyLogService;

    public VaultService(
            VaultRepository vaultRepository,
            DocumentService documentService,
            PrivacyLogService privacyLogService) {
        this.vaultRepository = vaultRepository;
        this.documentService = documentService;
        this.privacyLogService = privacyLogService;
    }

    public Vault create(String name) {
        Vault vault = Vault.builder()
                .id(UUID.randomUUID().toString())
                .name(name == null || name.isBlank() ? "Nuovo vault" : name.trim())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Vault saved = vaultRepository.save(vault);
        privacyLogService.log("Creato vault locale: " + saved.getId() + " (" + saved.getName() + ")");
        return saved;
    }

    public Optional<Vault> findById(String vaultId) {
        return vaultRepository.findById(vaultId);
    }

    public List<Vault> listVaults() {
        return vaultRepository.listAll();
    }

    public List<FileIndexStatus> addDocuments(String vaultId, MultipartFile[] files) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new IllegalArgumentException("Vault non trovato: " + vaultId));
        List<FileIndexStatus> statuses = new ArrayList<>();
        for (MultipartFile file : files) {
            FileIndexStatus status = documentService.ingest(file);
            statuses.add(status);
            if (status.getDocumentId() != null) {
                vault.getDocIds().add(status.getDocumentId());
            }
        }
        vaultRepository.save(vault);
        privacyLogService.log("Aggiunti " + statuses.size() + " file al vault " + vaultId);
        return statuses;
    }

    public void addExistingDocument(String vaultId, String docId) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new IllegalArgumentException("Vault non trovato: " + vaultId));
        if (documentService.findDocument(docId).isEmpty()) {
            throw new IllegalArgumentException("Documento non trovato: " + docId);
        }
        vault.getDocIds().add(docId);
        vaultRepository.save(vault);
        privacyLogService.log("Associato documento " + docId + " al vault " + vaultId);
    }

    public void removeDocument(String vaultId, String docId) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new IllegalArgumentException("Vault non trovato: " + vaultId));
        Set<String> docIds = vault.getDocIds();
        if (!docIds.remove(docId)) {
            throw new IllegalArgumentException("Documento non presente nel vault: " + docId);
        }
        vaultRepository.save(vault);
        privacyLogService.log("Rimosso documento " + docId + " dal vault " + vaultId);
    }

    public List<KnowledgeDocument> resolveDocuments(Vault vault) {
        return vault.getDocIds().stream()
                .map(documentService::findDocument)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public void deleteVault(String vaultId) {
        vaultRepository.delete(vaultId);
        privacyLogService.log("Eliminato vault locale: " + vaultId);
    }
}
