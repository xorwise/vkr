package ru.vkr.transport.controller;

import org.springframework.web.bind.annotation.*;
import ru.vkr.transport.dto.SparqlResultDto;
import ru.vkr.transport.ontology.SparqlService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SparqlController {

    private final SparqlService sparqlService;

    public SparqlController(SparqlService sparqlService) {
        this.sparqlService = sparqlService;
    }

    /**
     * POST /api/sparql
     * Execute arbitrary SPARQL SELECT or ASK query
     * Body: { "query": "SELECT ..." }
     */
    @PostMapping("/sparql")
    public SparqlResultDto executeSparql(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        if (query == null || query.isBlank()) {
            SparqlResultDto error = new SparqlResultDto();
            error.setError("Missing 'query' field in request body");
            return error;
        }
        return sparqlService.execute(query);
    }
}
