package it.uniroma3.sii.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.uniroma3.sii.dto.OnboardingInitRequest;
import it.uniroma3.sii.model.PrismSettings;
import it.uniroma3.sii.service.SettingsService;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final SettingsService settingsService;

    public OnboardingController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @PostMapping("/init")
    public ResponseEntity<PrismSettings> init(@RequestBody OnboardingInitRequest request) {
        return ResponseEntity.ok(settingsService.initialize(request));
    }
}
