package it.uniroma3.sii.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.uniroma3.sii.dto.DataDeleteRequest;
import it.uniroma3.sii.dto.DataDeleteResponse;
import it.uniroma3.sii.service.DataManagementService;

@RestController
@RequestMapping("/api/data")
public class DataController {

    private final DataManagementService dataManagementService;

    public DataController(DataManagementService dataManagementService) {
        this.dataManagementService = dataManagementService;
    }

    @PostMapping("/delete")
    public ResponseEntity<DataDeleteResponse> delete(@RequestBody DataDeleteRequest request) {
        return ResponseEntity.ok(dataManagementService.delete(request));
    }
}
