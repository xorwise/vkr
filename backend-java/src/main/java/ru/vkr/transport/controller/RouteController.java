package ru.vkr.transport.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.vkr.transport.dto.RouteDto;
import ru.vkr.transport.ontology.OntologyService;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final OntologyService ontologyService;

    public RouteController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @GetMapping
    public List<RouteDto> getAllRoutes(@RequestParam(required = false) String type) {
        return ontologyService.getAllRoutes(type);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RouteDto> getRouteById(@PathVariable String id) {
        return ontologyService.getRouteById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
