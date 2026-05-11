package it.uniroma3.sii.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.uniroma3.sii.dto.SystemStatusResponse;
import it.uniroma3.sii.service.SystemStatusService;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemStatusService statusService;

    public SystemController(SystemStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public ResponseEntity<SystemStatusResponse> status() {
        return ResponseEntity.ok(statusService.status());
    }
}
