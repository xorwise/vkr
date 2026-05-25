package ru.vkr.transport.ontology;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.ModelFactory;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.vkr.transport.config.OntologyConfig;
import ru.vkr.transport.dto.SparqlResultDto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
public class SparqlService {

    private static final Logger log = LoggerFactory.getLogger(SparqlService.class);

    private final OWLOntology ontology;
    private final OWLOntologyManager manager;

    @Value("${ontology.iri}")
    private String ontologyIri;

    public SparqlService(OntologyConfig.OntologyBundle bundle) {
        this.ontology = bundle.getOntology();
        this.manager = bundle.getManager();
    }

    /**
     * Execute arbitrary SPARQL query against the ontology.
     * Supports SELECT and ASK.
     */
    public SparqlResultDto execute(String sparqlQuery) {
        SparqlResultDto result = new SparqlResultDto();
        try {
            OntModel model = buildJenaModel();
            Query query = QueryFactory.create(sparqlQuery);

            if (query.isAskType()) {
                result.setQueryType("ASK");
                try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
                    result.setBooleanResult(qe.execAsk());
                }
            } else if (query.isSelectType()) {
                result.setQueryType("SELECT");
                List<Map<String, String>> rows = new ArrayList<>();
                try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
                    ResultSet rs = qe.execSelect();
                    result.setVariables(rs.getResultVars());
                    while (rs.hasNext()) {
                        QuerySolution sol = rs.next();
                        Map<String, String> row = new LinkedHashMap<>();
                        for (String var : rs.getResultVars()) {
                            if (sol.contains(var)) {
                                var node = sol.get(var);
                                if (node.isURIResource()) {
                                    String uri = node.asResource().getURI();
                                    // Return just the fragment for readability
                                    String frag = uri.contains("#") ? uri.substring(uri.lastIndexOf('#') + 1) : uri;
                                    row.put(var, frag);
                                } else if (node.isLiteral()) {
                                    row.put(var, node.asLiteral().getString());
                                } else {
                                    row.put(var, node.toString());
                                }
                            } else {
                                row.put(var, null);
                            }
                        }
                        rows.add(row);
                    }
                }
                result.setResults(rows);
            } else {
                result.setError("Only SELECT and ASK queries are supported");
            }
        } catch (Exception e) {
            log.error("SPARQL error: {}", e.getMessage(), e);
            result.setError(e.getMessage());
        }
        return result;
    }

    /**
     * Build Jena OntModel from current OWL ontology (via RDF/XML serialization).
     */
    public OntModel buildJenaModel() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        manager.saveOntology(ontology, new RDFXMLDocumentFormat(), baos);
        OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        model.read(new ByteArrayInputStream(baos.toByteArray()), null, "RDF/XML");
        return model;
    }

    // ===== BUILT-IN QUERIES =====

    private static final String PREFIX = "PREFIX tr: <http://www.semanticweb.org/ontologies/TransportRoutes#>\n" +
                                          "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n";

    public List<Map<String, String>> getRoutesReachingObject(String objectId) {
        String q = PREFIX +
            "SELECT DISTINCT ?route WHERE {\n" +
            "  ?route rdf:type tr:Route .\n" +
            "  ?route tr:reachesObject tr:" + objectId + " .\n" +
            "}";
        return selectList(q);
    }

    public List<Map<String, String>> getRoutesReachingViaTransfer(String objectId) {
        String q = PREFIX +
            "SELECT DISTINCT ?route WHERE {\n" +
            "  ?route rdf:type tr:Route .\n" +
            "  ?route tr:reachesObjectViaTransfer tr:" + objectId + " .\n" +
            "}";
        return selectList(q);
    }

    /**
     * Detailed via-transfer query: for each route1 that can reach objectId via transfer,
     * return which transfer stop and route2 is used.
     * Returns: {route, transferStop, transferStopName, route2}
     */
    public List<Map<String, String>> getRoutesReachingViaTransferDetail(String objectId) {
        String q = PREFIX +
            "SELECT DISTINCT ?route ?transferStop ?transferStopName ?route2 WHERE {\n" +
            "  ?route rdf:type tr:Route .\n" +
            "  ?route tr:stopLink ?sl .\n" +
            "  { ?sl tr:beginStop ?transferStop . } UNION { ?sl tr:endStop ?transferStop . }\n" +
            "  ?transferStop tr:transferTo ?route2 .\n" +
            "  ?route2 tr:reachesObject tr:" + objectId + " .\n" +
            "  FILTER(?route != ?route2)\n" +
            "  OPTIONAL { ?transferStop tr:stopName ?transferStopName . }\n" +
            "}";
        return selectList(q);
    }

    public List<Map<String, String>> getRoutesConnecting(String fromId, String toId) {
        String q = PREFIX +
            "SELECT DISTINCT ?route WHERE {\n" +
            "  ?route rdf:type tr:Route .\n" +
            "  ?route tr:reachesObject tr:" + fromId + " .\n" +
            "  ?route tr:reachesObject tr:" + toId + " .\n" +
            "}";
        return selectList(q);
    }

    public List<Map<String, String>> getObjectsReachableFromStop(String stopId) {
        String q = PREFIX +
            "SELECT DISTINCT ?object ?name WHERE {\n" +
            "  ?route tr:stopLink ?sl .\n" +
            "  { ?sl tr:beginStop tr:" + stopId + " . } UNION { ?sl tr:endStop tr:" + stopId + " . }\n" +
            "  ?route tr:reachesObject ?object .\n" +
            "  OPTIONAL { ?object tr:objectName ?name . }\n" +
            "}";
        return selectList(q);
    }

    public List<Map<String, String>> getObjectsReachableViaTransfer(String stopId) {
        String q = PREFIX +
            "SELECT DISTINCT ?object ?name WHERE {\n" +
            "  ?route tr:stopLink ?sl .\n" +
            "  { ?sl tr:beginStop tr:" + stopId + " . } UNION { ?sl tr:endStop tr:" + stopId + " . }\n" +
            "  ?route tr:reachesObjectViaTransfer ?object .\n" +
            "  OPTIONAL { ?object tr:objectName ?name . }\n" +
            "}";
        return selectList(q);
    }

    public boolean canReach(String routeId, String objectId) {
        String q = PREFIX +
            "ASK { tr:" + routeId + " tr:reachesObject tr:" + objectId + " . }";
        SparqlResultDto r = execute(q);
        return Boolean.TRUE.equals(r.getBooleanResult());
    }

    public List<Map<String, String>> getTransferStopsBetweenRoutes(String route1Id, String route2Id) {
        String q = PREFIX +
            "SELECT DISTINCT ?stop ?name WHERE {\n" +
            "  ?stop rdf:type tr:Stop .\n" +
            "  ?stop tr:transferTo tr:" + route1Id + " .\n" +
            "  ?stop tr:transferTo tr:" + route2Id + " .\n" +
            "  OPTIONAL { ?stop tr:stopName ?name . }\n" +
            "}";
        return selectList(q);
    }

    public List<Map<String, String>> getMedicalFacilities() {
        // Jena OWL_MEM has no inference — query both subclasses directly
        String q = PREFIX +
            "SELECT DISTINCT ?obj ?name WHERE {\n" +
            "  { ?obj rdf:type tr:Hospital . } UNION { ?obj rdf:type tr:Polyclinic . }\n" +
            "  OPTIONAL { ?obj tr:objectName ?name . }\n" +
            "}";
        return selectList(q);
    }

    public List<Map<String, String>> getEducationalFacilities() {
        // Jena OWL_MEM has no inference — query both subclasses directly
        String q = PREFIX +
            "SELECT DISTINCT ?obj ?name WHERE {\n" +
            "  { ?obj rdf:type tr:School . } UNION { ?obj rdf:type tr:University . }\n" +
            "  OPTIONAL { ?obj tr:objectName ?name . }\n" +
            "}";
        return selectList(q);
    }

    public List<Map<String, String>> getTransferStops() {
        String q = PREFIX +
            "SELECT DISTINCT ?stop ?name WHERE {\n" +
            "  ?stop rdf:type tr:TransferPoint .\n" +
            "  OPTIONAL { ?stop tr:stopName ?name . }\n" +
            "}";
        return selectList(q);
    }

    // ===== HELPER =====

    private List<Map<String, String>> selectList(String query) {
        SparqlResultDto r = execute(query);
        return r.getResults() != null ? r.getResults() : Collections.emptyList();
    }
}
