package ru.vkr.transport.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.vkr.transport.dto.CityObjectDto;
import ru.vkr.transport.ontology.OntologyService;

import java.util.List;

@RestController
@RequestMapping("/api/objects")
public class ObjectController {

    private final OntologyService ontologyService;

    public ObjectController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @GetMapping
    public List<CityObjectDto> getAllObjects(@RequestParam(required = false) String category) {
        return ontologyService.getAllObjects(category);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CityObjectDto> getObjectById(@PathVariable String id) {
        return ontologyService.getObjectById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
