package ru.vkr.transport.controller;

import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.vkr.transport.config.OntologyConfig;
import ru.vkr.transport.ontology.OntologyService;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ontology")
public class OntologyController {

    private final OntologyService ontologyService;
    private final OWLOntology ontology;
    private final OWLOntologyManager manager;

    public OntologyController(OntologyService ontologyService,
                               OntologyConfig.OntologyBundle bundle) {
        this.ontologyService = ontologyService;
        this.ontology = bundle.getOntology();
        this.manager = bundle.getManager();
    }

    /**
     * GET /api/ontology/classes
     * All classes with individual counts
     */
    @GetMapping("/classes")
    public List<Map<String, Object>> getClasses() {
        return ontologyService.getAllClasses();
    }

    /**
     * GET /api/ontology/properties
     * All object and data properties
     */
    @GetMapping("/properties")
    public List<Map<String, String>> getProperties() {
        return ontologyService.getAllProperties();
    }

    /**
     * GET /api/ontology/individuals
     * All named individuals with their class
     */
    @GetMapping("/individuals")
    public List<Map<String, String>> getIndividuals() {
        return ontologyService.getAllIndividuals();
    }

    /**
     * GET /api/ontology/export
     * Download current OWL file as RDF/XML
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportOntology() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        manager.saveOntology(ontology, new RDFXMLDocumentFormat(), baos);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=TransportRoutes.owl")
            .contentType(MediaType.APPLICATION_XML)
            .body(baos.toByteArray());
    }
}
