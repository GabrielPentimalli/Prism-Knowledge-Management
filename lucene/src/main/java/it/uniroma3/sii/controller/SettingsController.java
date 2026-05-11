package it.uniroma3.sii.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.uniroma3.sii.dto.SettingsUpdateRequest;
import it.uniroma3.sii.model.PrismSettings;
import it.uniroma3.sii.service.SettingsService;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> get() {
        return ResponseEntity.ok(settingsService.settingsWithPrivacyLog());
    }

    @PutMapping
    public ResponseEntity<PrismSettings> update(@RequestBody SettingsUpdateRequest request) {
        return ResponseEntity.ok(settingsService.update(request));
    }
}
