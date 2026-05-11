package it.uniroma3.sii.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import it.uniroma3.sii.dto.DataDeleteRequest;
import it.uniroma3.sii.dto.DataDeleteResponse;
import it.uniroma3.sii.model.ChatScopeType;
import it.uniroma3.sii.model.Vault;
import it.uniroma3.sii.service.storage.ChatSessionRepository;
import it.uniroma3.sii.service.storage.PrivacyLogService;
import it.uniroma3.sii.service.storage.StoragePathService;

@Service
public class DataManagementService {

    private final StoragePathService paths;
    private final DocumentService documentService;
    private final VaultService vaultService;
    private final ChatSessionRepository chatSessionRepository;
    private final PrivacyLogService privacyLogService;

    public DataManagementService(
            StoragePathService paths,
            DocumentService documentService,
            VaultService vaultService,
            ChatSessionRepository chatSessionRepository,
            PrivacyLogService privacyLogService) {
        this.paths = paths;
        this.documentService = documentService;
        this.vaultService = vaultService;
        this.chatSessionRepository = chatSessionRepository;
        this.privacyLogService = privacyLogService;
    }

    public DataDeleteResponse delete(DataDeleteRequest request) {
        String mode = request.getMode() == null ? "" : request.getMode().trim().toUpperCase();
        if ("TOTAL".equals(mode)) {
            deleteTotal();
            return DataDeleteResponse.builder().success(true).message("Tutti i dati locali sono stati eliminati.").build();
        }
        deleteSelective(request);
        return DataDeleteResponse.builder().success(true).message("Eliminazione selettiva completata.").build();
    }

    private void deleteTotal() {
        deleteDir(paths.documentsDir());
        deleteDir(paths.vaultsDir());
        deleteDir(paths.indicesDir());
        deleteDir(paths.chatsDir());
        paths.ensureBaseDirectories();
        privacyLogService.log("Cancellazione totale dati locali");
    }

    private void deleteSelective(DataDeleteRequest request) {
        if (request.getDocumentId() != null && !request.getDocumentId().isBlank()) {
            removeDocumentEverywhere(request.getDocumentId());
        }
        if (request.getVaultId() != null && !request.getVaultId().isBlank()) {
            vaultService.deleteVault(request.getVaultId());
            chatSessionRepository.deleteScope(ChatScopeType.VAULT, request.getVaultId());
        }
        if (request.getScopeType() != null && request.getScopeId() != null && !request.getScopeId().isBlank()) {
            chatSessionRepository.deleteScope(request.getScopeType(), request.getScopeId());
        }
        privacyLogService.log("Cancellazione selettiva dati locali");
    }

    private void removeDocumentEverywhere(String docId) {
        documentService.deleteDocument(docId);
        for (Vault vault : vaultService.listVaults()) {
            if (vault.getDocIds().contains(docId)) {
                vaultService.removeDocument(vault.getId(), docId);
            }
        }
        chatSessionRepository.deleteScope(ChatScopeType.DOCUMENT, docId);
    }

    private void deleteDir(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException e) {
                    throw new IllegalStateException("Impossibile eliminare " + item, e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile eliminare " + path, e);
        }
    }
}
