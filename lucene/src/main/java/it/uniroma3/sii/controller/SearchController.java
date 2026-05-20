package it.uniroma3.sii.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import it.uniroma3.sii.service.DocumentService;
import it.uniroma3.sii.service.SettingsService;
import it.uniroma3.sii.service.VaultService;

@Controller
public class SearchController {

    private final SettingsService settingsService;
    private final DocumentService documentService;
    private final VaultService vaultService;

    public SearchController(
            SettingsService settingsService,
            DocumentService documentService,
            VaultService vaultService) {
        this.settingsService = settingsService;
        this.documentService = documentService;
        this.vaultService = vaultService;
    }

    @GetMapping("/")
    public String root() {
        return settingsService.isOnboardingCompleted() ? "redirect:/home" : "redirect:/onboarding";
    }

    @GetMapping("/onboarding")
    public String onboarding() {
        return "onboarding";
    }

    @GetMapping("/home")
    public String home(Model model) {
        model.addAttribute("recentDocuments", documentService.listDocuments());
        model.addAttribute("vaults", vaultService.listVaults());
        return "home";
    }

    @GetMapping("/workspace/document")
    public String documentWorkspace(@RequestParam(name = "docId", required = false) String docId, Model model) {
        model.addAttribute("docId", docId);
        model.addAttribute("documents", documentService.listDocuments());
        return "document_workspace";
    }

    @GetMapping("/workspace/vault")
    public String vaultWorkspace(@RequestParam(name = "vaultId", required = false) String vaultId, RedirectAttributes redirectAttributes) {
        if (vaultId != null && !vaultId.isBlank()) {
            redirectAttributes.addAttribute("vaultId", vaultId);
        }
        return "redirect:/workspace/vault/documents";
    }

    @GetMapping("/workspace/vault/documents")
    public String vaultDocumentsWorkspace(@RequestParam(name = "vaultId", required = false) String vaultId, Model model) {
        model.addAttribute("vaultId", vaultId);
        return "vault_workspace";
    }

    @GetMapping("/workspace/vault/chat")
    public String vaultChatWorkspace(@RequestParam(name = "vaultId", required = false) String vaultId, Model model) {
        model.addAttribute("vaultId", vaultId);
        return "vault_workspace_chat";
    }

    @GetMapping("/document/{docId}")
    public String documentViewer(@PathVariable("docId") String docId, Model model) {
        model.addAttribute("docId", docId);
        return "document_viewer";
    }

    @GetMapping("/search/global")
    public String globalSearchPage(Model model) {
        model.addAttribute("vaults", vaultService.listVaults());
        return "global_search";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("settings", settingsService.getSettings());
        return "settings";
    }
}
