package ru.vkr.transport.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.vkr.transport.dto.StopDto;
import ru.vkr.transport.ontology.OntologyService;

import java.util.List;

@RestController
@RequestMapping("/api/stops")
public class StopController {

    private final OntologyService ontologyService;

    public StopController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @GetMapping
    public List<StopDto> getAllStops() {
        return ontologyService.getAllStops();
    }

    @GetMapping("/transfer")
    public List<StopDto> getTransferStops() {
        return ontologyService.getTransferStops();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StopDto> getStopById(@PathVariable String id) {
        return ontologyService.getStopById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
