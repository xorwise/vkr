package ru.vkr.transport.ontology;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.vkr.transport.config.OntologyConfig;
import ru.vkr.transport.dto.CityObjectDto;
import ru.vkr.transport.dto.RouteDto;
import ru.vkr.transport.dto.StopDto;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OntologyService {

    private static final Logger log = LoggerFactory.getLogger(OntologyService.class);

    private final OWLOntology ontology;
    private final OWLDataFactory dataFactory;
    private final OWLReasoner reasoner;
    private final OWLOntologyManager manager;

    // HermiT is not thread-safe — all reasoner access must be synchronized
    private final Object reasonerLock = new Object();

    @Value("${ontology.iri}")
    private String ontologyIri;

    public OntologyService(OntologyConfig.OntologyBundle bundle) {
        this.ontology = bundle.getOntology();
        this.dataFactory = bundle.getManager().getOWLDataFactory();
        this.reasoner = bundle.getReasoner();
        this.manager = bundle.getManager();
    }

    public String getIri() {
        return ontologyIri;
    }

    public IRI iri(String localName) {
        return IRI.create(ontologyIri + localName);
    }

    public OWLClass getClass(String localName) {
        return dataFactory.getOWLClass(iri(localName));
    }

    public OWLObjectProperty getObjectProperty(String localName) {
        return dataFactory.getOWLObjectProperty(iri(localName));
    }

    public OWLDataProperty getDataProperty(String localName) {
        return dataFactory.getOWLDataProperty(iri(localName));
    }

    // ===== INDEXES (built lazily, cleared on any write) =====
    // Using direct axiom traversal — no HermiT calls for data reads

    /** Returns all named individuals asserted to be of the given class (ClassAssertion axioms only). */
    private Set<OWLNamedIndividual> getDirectInstances(String className) {
        OWLClass cls = getClass(className);
        Set<OWLNamedIndividual> result = new HashSet<>();
        for (OWLClassAssertionAxiom ax : ontology.getAxioms(AxiomType.CLASS_ASSERTION)) {
            if (ax.getClassExpression().equals(cls) && ax.getIndividual() instanceof OWLNamedIndividual ni) {
                result.add(ni);
            }
        }
        return result;
    }

    /** Returns all values of objectProperty for subject, using explicit axioms only. */
    private Set<OWLNamedIndividual> getDirectObjectValues(OWLNamedIndividual subject, OWLObjectProperty prop) {
        Set<OWLNamedIndividual> result = new HashSet<>();
        for (OWLObjectPropertyAssertionAxiom ax : ontology.getObjectPropertyAssertionAxioms(subject)) {
            if (ax.getProperty().equals(prop) && ax.getObject() instanceof OWLNamedIndividual ni) {
                result.add(ni);
            }
        }
        return result;
    }

    // ===== STOPS =====

    public synchronized List<StopDto> getAllStops() {
        Set<OWLNamedIndividual> individuals = getDirectInstances("Stop");
        List<StopDto> result = new ArrayList<>();
        for (OWLNamedIndividual ind : individuals) {
            result.add(buildStopDto(ind));
        }
        return result;
    }

    public synchronized Optional<StopDto> getStopById(String id) {
        OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(iri(id));
        if (!ontology.containsIndividualInSignature(ind.getIRI())) {
            return Optional.empty();
        }
        return Optional.of(buildStopDto(ind));
    }

    public synchronized List<StopDto> getTransferStops() {
        // TransferPoint individuals are materialized as explicit ClassAssertion axioms
        Set<OWLNamedIndividual> individuals = getDirectInstances("TransferPoint");
        return individuals.stream().map(this::buildStopDto).collect(Collectors.toList());
    }

    private StopDto buildStopDto(OWLNamedIndividual ind) {
        StopDto dto = new StopDto();
        dto.setId(fragmentOf(ind));
        getDataPropertyValue(ind, "stopName").ifPresent(dto::setName);
        getDataPropertyValue(ind, "latitude").ifPresent(v -> {
            try { dto.setLatitude(Double.parseDouble(v)); } catch (Exception ignored) {}
        });
        getDataPropertyValue(ind, "longitude").ifPresent(v -> {
            try { dto.setLongitude(Double.parseDouble(v)); } catch (Exception ignored) {}
        });
        getDataPropertyValue(ind, "osmId").ifPresent(dto::setOsmId);
        // TransferPoint: check via direct class assertion
        dto.setTransferPoint(getDirectInstances("TransferPoint").contains(ind));
        return dto;
    }

    // ===== ROUTES =====

    public synchronized List<RouteDto> getAllRoutes(String typeFilter) {
        Set<OWLNamedIndividual> individuals = getDirectInstances("Route");
        List<RouteDto> result = new ArrayList<>();
        for (OWLNamedIndividual ind : individuals) {
            RouteDto dto = buildRouteDto(ind);
            if (typeFilter == null || typeFilter.isBlank() ||
                typeFilter.equalsIgnoreCase(dto.getTransportType())) {
                result.add(dto);
            }
        }
        return result;
    }

    public synchronized Optional<RouteDto> getRouteById(String id) {
        OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(iri(id));
        if (!ontology.containsIndividualInSignature(ind.getIRI())) {
            return Optional.empty();
        }
        return Optional.of(buildRouteDto(ind));
    }

    private RouteDto buildRouteDto(OWLNamedIndividual ind) {
        RouteDto dto = new RouteDto();
        String localName = fragmentOf(ind);
        dto.setId(localName);

        // routeTransport → transportType via data property or class membership (by axioms)
        OWLObjectProperty routeTransportProp = getObjectProperty("routeTransport");
        for (OWLNamedIndividual transport : getDirectObjectValues(ind, routeTransportProp)) {
            dto.setTransportId(fragmentOf(transport));
            getDataPropertyValue(transport, "transportType").ifPresent(dto::setTransportType);
            if (dto.getTransportType() == null) {
                dto.setTransportType(inferTransportTypeByAxioms(transport));
            }
        }

        // routeRef
        getDataPropertyValue(ind, "routeRef").ifPresentOrElse(
            dto::setRouteRef,
            () -> {
                // fallback: parse from name Route_Bus_6_12345 -> "6"
                String[] parts = localName.split("_");
                if (parts.length >= 3) dto.setRouteRef(parts[parts.length - 2]);
            }
        );

        if (dto.getTransportType() == null) {
            String[] parts = localName.split("_");
            if (parts.length >= 2) dto.setTransportType(parts[1]);
        }

        getDataPropertyValue(ind, "osmId").ifPresent(dto::setOsmId);
        dto.setStopIds(getRouteStops(ind));
        return dto;
    }

    private String inferTransportTypeByAxioms(OWLNamedIndividual transport) {
        for (String t : new String[]{"Bus", "Tram", "Trolleybus", "Metro"}) {
            OWLClass cls = getClass(t);
            for (OWLClassAssertionAxiom ax : ontology.getClassAssertionAxioms(transport)) {
                if (ax.getClassExpression().equals(cls)) return t;
            }
        }
        return "Unknown";
    }

    private List<String> getRouteStops(OWLNamedIndividual route) {
        OWLObjectProperty stopLinkProp  = getObjectProperty("stopLink");
        OWLObjectProperty beginStopProp = getObjectProperty("beginStop");
        OWLObjectProperty endStopProp   = getObjectProperty("endStop");
        OWLDataProperty   stopOrderProp = getDataProperty("stopOrder");

        // Get links via direct axioms
        List<OWLNamedIndividual> sortedLinks = getDirectObjectValues(route, stopLinkProp).stream()
            .sorted((a, b) -> {
                int oa = ontology.getDataPropertyAssertionAxioms(a).stream()
                    .filter(ax -> ax.getProperty().equals(stopOrderProp))
                    .mapToInt(ax -> { try { return Integer.parseInt(ax.getObject().getLiteral()); } catch (Exception e2) { return Integer.MAX_VALUE; } })
                    .findFirst().orElse(Integer.MAX_VALUE);
                int ob = ontology.getDataPropertyAssertionAxioms(b).stream()
                    .filter(ax -> ax.getProperty().equals(stopOrderProp))
                    .mapToInt(ax -> { try { return Integer.parseInt(ax.getObject().getLiteral()); } catch (Exception e2) { return Integer.MAX_VALUE; } })
                    .findFirst().orElse(Integer.MAX_VALUE);
                if (oa != ob) return Integer.compare(oa, ob);
                return fragmentOf(a).compareTo(fragmentOf(b));
            })
            .collect(Collectors.toList());

        List<String> stopIds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < sortedLinks.size(); i++) {
            OWLNamedIndividual link = sortedLinks.get(i);
            if (i == 0) {
                getDirectObjectValues(link, beginStopProp).stream()
                    .map(this::fragmentOf).filter(seen::add).forEach(stopIds::add);
            }
            getDirectObjectValues(link, endStopProp).stream()
                .map(this::fragmentOf).filter(seen::add).forEach(stopIds::add);
        }
        return stopIds;
    }

    // ===== CITY OBJECTS =====

    /** Returns individuals of CityObject and all known subclasses. */
    private Set<OWLNamedIndividual> getAllObjectIndividuals() {
        String[] objClasses = {"Hospital", "Polyclinic", "School", "University",
                               "Store", "ShoppingCenter", "Park", "Square", "Garden", "Monument", "CityObject"};
        Set<OWLNamedIndividual> result = new HashSet<>();
        for (String cls : objClasses) result.addAll(getDirectInstances(cls));
        return result;
    }

    public synchronized List<CityObjectDto> getAllObjects(String categoryFilter) {
        Set<OWLNamedIndividual> individuals = getAllObjectIndividuals();
        List<CityObjectDto> result = new ArrayList<>();
        for (OWLNamedIndividual ind : individuals) {
            CityObjectDto dto = buildObjectDto(ind);
            if (categoryFilter == null || categoryFilter.isBlank() ||
                categoryFilter.equalsIgnoreCase(dto.getCategory())) {
                result.add(dto);
            }
        }
        return result;
    }

    public synchronized Optional<CityObjectDto> getObjectById(String id) {
        OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(iri(id));
        if (!ontology.containsIndividualInSignature(ind.getIRI())) {
            return Optional.empty();
        }
        return Optional.of(buildObjectDto(ind));
    }

    private CityObjectDto buildObjectDto(OWLNamedIndividual ind) {
        CityObjectDto dto = new CityObjectDto();
        dto.setId(fragmentOf(ind));
        getDataPropertyValue(ind, "objectName").ifPresent(dto::setName);
        getDataPropertyValue(ind, "objectCategory").ifPresent(dto::setCategory);
        getDataPropertyValue(ind, "latitude").ifPresent(v -> {
            try { dto.setLatitude(Double.parseDouble(v)); } catch (Exception ignored) {}
        });
        getDataPropertyValue(ind, "longitude").ifPresent(v -> {
            try { dto.setLongitude(Double.parseDouble(v)); } catch (Exception ignored) {}
        });
        dto.setType(inferObjectTypeByAxioms(ind));
        if (dto.getCategory() == null) dto.setCategory(inferCategory(dto.getType()));
        OWLObjectProperty standsNear = getObjectProperty("standsNear");
        getDirectObjectValues(ind, standsNear).stream().findFirst()
            .ifPresent(s -> dto.setNearStopId(fragmentOf(s)));
        return dto;
    }

    private String inferObjectTypeByAxioms(OWLNamedIndividual ind) {
        String[] types = {"Hospital", "Polyclinic", "School", "University",
                          "Store", "ShoppingCenter", "Park", "Square", "Garden", "Monument"};
        for (String t : types) {
            OWLClass cls = getClass(t);
            for (OWLClassAssertionAxiom ax : ontology.getClassAssertionAxioms(ind)) {
                if (ax.getClassExpression().equals(cls)) return t;
            }
        }
        return "CityObject";
    }

    private String inferCategory(String type) {
        return switch (type) {
            case "Hospital", "Polyclinic" -> "medical";
            case "School", "University" -> "educational";
            case "Store", "ShoppingCenter" -> "shopping";
            case "Park", "Square", "Garden" -> "recreation";
            case "Monument" -> "culture";
            default -> "other";
        };
    }

    // ===== ONTOLOGY META =====

    public synchronized List<Map<String, Object>> getAllClasses() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (OWLClass cls : ontology.getClassesInSignature()) {
            if (cls.isOWLThing() || cls.isOWLNothing()) continue;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("iri", cls.getIRI().toString());
            map.put("name", cls.getIRI().getFragment());
            // Count via direct class assertions (fast, no reasoner)
            long count = ontology.getAxioms(AxiomType.CLASS_ASSERTION).stream()
                .filter(ax -> ax.getClassExpression().equals(cls))
                .count();
            map.put("individualCount", count);
            result.add(map);
        }
        return result;
    }

    public synchronized List<Map<String, String>> getAllProperties() {
        List<Map<String, String>> result = new ArrayList<>();
        for (OWLObjectProperty prop : ontology.getObjectPropertiesInSignature()) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("iri", prop.getIRI().toString());
            map.put("name", prop.getIRI().getFragment());
            map.put("type", "ObjectProperty");
            result.add(map);
        }
        for (OWLDataProperty prop : ontology.getDataPropertiesInSignature()) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("iri", prop.getIRI().toString());
            map.put("name", prop.getIRI().getFragment());
            map.put("type", "DataProperty");
            result.add(map);
        }
        return result;
    }

    public synchronized List<Map<String, String>> getAllIndividuals() {
        List<Map<String, String>> result = new ArrayList<>();
        for (OWLNamedIndividual ind : ontology.getIndividualsInSignature()) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("iri", ind.getIRI().toString());
            map.put("name", fragmentOf(ind));
            // Get direct class from ClassAssertion axioms (no reasoner)
            String cls = ontology.getClassAssertionAxioms(ind).stream()
                .map(OWLClassAssertionAxiom::getClassExpression)
                .filter(c -> !c.isOWLThing() && c instanceof OWLClass)
                .map(c -> ((OWLClass) c).getIRI().getFragment())
                .findFirst().orElse("owl:Thing");
            map.put("class", cls);
            result.add(map);
        }
        return result;
    }

    // ===== ADD INDIVIDUALS (для импорта) =====

    public synchronized void addIndividualToClass(String individualId, String className) {
        OWLClass cls = getClass(className);
        OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(iri(individualId));
        OWLAxiom axiom = dataFactory.getOWLClassAssertionAxiom(cls, ind);
        ontology.getOWLOntologyManager().addAxiom(ontology, axiom);
    }

    public synchronized void setDataProperty(String individualId, String propertyName, String value) {
        OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(iri(individualId));
        OWLDataProperty prop = getDataProperty(propertyName);
        OWLAxiom axiom = dataFactory.getOWLDataPropertyAssertionAxiom(prop, ind,
            dataFactory.getOWLLiteral(value));
        ontology.getOWLOntologyManager().addAxiom(ontology, axiom);
    }

    public synchronized void replaceDataProperty(String individualId, String propertyName, String value) {
        removeDataProperty(individualId, propertyName);
        setDataProperty(individualId, propertyName, value);
    }

    public synchronized void setDataPropertyDouble(String individualId, String propertyName, double value) {
        OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(iri(individualId));
        OWLDataProperty prop = getDataProperty(propertyName);
        OWLAxiom axiom = dataFactory.getOWLDataPropertyAssertionAxiom(prop, ind,
            dataFactory.getOWLLiteral(value));
        ontology.getOWLOntologyManager().addAxiom(ontology, axiom);
    }

    public synchronized void setDataPropertyInt(String individualId, String propertyName, int value) {
        OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(iri(individualId));
        OWLDataProperty prop = getDataProperty(propertyName);
        OWLAxiom axiom = dataFactory.getOWLDataPropertyAssertionAxiom(prop, ind,
            dataFactory.getOWLLiteral(value));
        ontology.getOWLOntologyManager().addAxiom(ontology, axiom);
    }

    public synchronized void setObjectProperty(String subjectId, String propertyName, String objectId) {
        OWLNamedIndividual subject = dataFactory.getOWLNamedIndividual(iri(subjectId));
        OWLNamedIndividual object = dataFactory.getOWLNamedIndividual(iri(objectId));
        OWLObjectProperty prop = getObjectProperty(propertyName);
        OWLAxiom axiom = dataFactory.getOWLObjectPropertyAssertionAxiom(prop, subject, object);
        ontology.getOWLOntologyManager().addAxiom(ontology, axiom);
    }

    public OWLOntologyManager getManager() {
        return manager;
    }

    public synchronized void flushReasoner() {
        reasoner.flush();
    }

    /**
     * Materialize inferred object property assertions by executing SWRL rules programmatically.
     * Uses direct axiom traversal (NO HermiT calls) for performance with 600+ stops.
     *
     * Rule 1 (reachesObject):
     *   Route ?r → stopLink → StopsLink ?sl → endStop/beginStop → Stop ?s
     *   CityObject ?o → standsNear → ?s  ⟹  reachesObject(?r, ?o)
     *
     * Rule 2 (reachesObjectViaTransfer):
     *   Route ?r1 has Stop ?s → transferTo → Route ?r2 → reachesObject → CityObject ?o
     *   ⟹ reachesObjectViaTransfer(?r1, ?o)
     *
     * Rule 3 (TransferPoint):
     *   Stop ?s → transferTo ≥ 2 distinct Routes  ⟹  TransferPoint(?s)
     *
     * Rule 4 (connectsObjects):
     *   Route ?r → reachesObject → ?o  ⟹  connectsObjects(?r, ?o)
     *
     * @return number of newly added axioms
     */
    public synchronized int materializeInferences() {
        int count = 0;

        OWLObjectProperty stopLinkProp    = getObjectProperty("stopLink");
        OWLObjectProperty beginStopProp   = getObjectProperty("beginStop");
        OWLObjectProperty endStopProp     = getObjectProperty("endStop");
        OWLObjectProperty standsNearProp  = getObjectProperty("standsNear");
        OWLObjectProperty reachesObjProp  = getObjectProperty("reachesObject");
        OWLObjectProperty transferToProp  = getObjectProperty("transferTo");
        OWLObjectProperty reachesViaProp  = getObjectProperty("reachesObjectViaTransfer");
        OWLObjectProperty connectsObjProp = getObjectProperty("connectsObjects");
        OWLClass transferPointClass       = getClass("TransferPoint");

        // === Build indexes from explicit axioms (no HermiT) ===

        // standsNear: stop → {objects}
        Map<IRI, Set<IRI>> stopToObjects = new HashMap<>();
        for (OWLObjectPropertyAssertionAxiom ax : ontology.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
            if (!ax.getProperty().equals(standsNearProp)) continue;
            if (!(ax.getSubject() instanceof OWLNamedIndividual subj)) continue;
            if (!(ax.getObject() instanceof OWLNamedIndividual obj)) continue;
            // standsNear(object, stop) — object stands near stop
            stopToObjects.computeIfAbsent(obj.getIRI(), k -> new HashSet<>()).add(subj.getIRI());
        }
        log.debug("stopToObjects: {} stops have nearby objects", stopToObjects.size());

        // stopLink: route → {links}; beginStop/endStop: link → stop
        Map<IRI, Set<IRI>> routeToLinks = new HashMap<>();
        Map<IRI, Set<IRI>> linkToStops  = new HashMap<>();
        for (OWLObjectPropertyAssertionAxiom ax : ontology.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
            OWLObjectPropertyExpression prop = ax.getProperty();
            if (!(ax.getSubject() instanceof OWLNamedIndividual subj)) continue;
            if (!(ax.getObject() instanceof OWLNamedIndividual obj)) continue;
            if (prop.equals(stopLinkProp)) {
                routeToLinks.computeIfAbsent(subj.getIRI(), k -> new HashSet<>()).add(obj.getIRI());
            } else if (prop.equals(beginStopProp) || prop.equals(endStopProp)) {
                linkToStops.computeIfAbsent(subj.getIRI(), k -> new HashSet<>()).add(obj.getIRI());
            }
        }

        // route → {stops}
        Map<IRI, Set<IRI>> routeToStops = new HashMap<>();
        for (Map.Entry<IRI, Set<IRI>> e : routeToLinks.entrySet()) {
            Set<IRI> stops = new HashSet<>();
            for (IRI link : e.getValue()) {
                Set<IRI> linkStops = linkToStops.getOrDefault(link, Set.of());
                stops.addAll(linkStops);
            }
            routeToStops.put(e.getKey(), stops);
        }
        log.debug("routeToStops: {} routes", routeToStops.size());

        // transferTo: stop → {routes}
        Map<IRI, Set<IRI>> stopToRoutes = new HashMap<>();
        for (OWLObjectPropertyAssertionAxiom ax : ontology.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
            if (!ax.getProperty().equals(transferToProp)) continue;
            if (!(ax.getSubject() instanceof OWLNamedIndividual subj)) continue;
            if (!(ax.getObject() instanceof OWLNamedIndividual obj)) continue;
            stopToRoutes.computeIfAbsent(subj.getIRI(), k -> new HashSet<>()).add(obj.getIRI());
        }
        log.debug("stopToRoutes: {} transfer stops", stopToRoutes.size());

        // === Rule 1: reachesObject ===
        Map<IRI, Set<IRI>> routeToObjectsMap = new HashMap<>();
        for (Map.Entry<IRI, Set<IRI>> e : routeToStops.entrySet()) {
            IRI routeIri = e.getKey();
            Set<IRI> reachable = new HashSet<>();
            for (IRI stopIri : e.getValue()) {
                reachable.addAll(stopToObjects.getOrDefault(stopIri, Set.of()));
            }
            routeToObjectsMap.put(routeIri, reachable);
            OWLNamedIndividual route = dataFactory.getOWLNamedIndividual(routeIri);
            for (IRI objIri : reachable) {
                OWLNamedIndividual obj = dataFactory.getOWLNamedIndividual(objIri);
                OWLAxiom ax = dataFactory.getOWLObjectPropertyAssertionAxiom(reachesObjProp, route, obj);
                if (!ontology.containsAxiom(ax)) { manager.addAxiom(ontology, ax); count++; }
            }
        }
        log.info("Rule 1 (reachesObject): {} routes reach {} total object-links", routeToObjectsMap.size(),
            routeToObjectsMap.values().stream().mapToInt(Set::size).sum());

        // === Rule 3: TransferPoint ===
        for (Map.Entry<IRI, Set<IRI>> e : stopToRoutes.entrySet()) {
            if (e.getValue().size() >= 2) {
                OWLNamedIndividual stop = dataFactory.getOWLNamedIndividual(e.getKey());
                OWLAxiom ax = dataFactory.getOWLClassAssertionAxiom(transferPointClass, stop);
                if (!ontology.containsAxiom(ax)) { manager.addAxiom(ontology, ax); count++; }
            }
        }

        // === Rule 2: reachesObjectViaTransfer ===
        for (Map.Entry<IRI, Set<IRI>> e : routeToStops.entrySet()) {
            IRI route1Iri = e.getKey();
            Set<IRI> direct = routeToObjectsMap.getOrDefault(route1Iri, Set.of());
            Set<IRI> viaTransfer = new HashSet<>();
            for (IRI stopIri : e.getValue()) {
                for (IRI route2Iri : stopToRoutes.getOrDefault(stopIri, Set.of())) {
                    if (route2Iri.equals(route1Iri)) continue;
                    viaTransfer.addAll(routeToObjectsMap.getOrDefault(route2Iri, Set.of()));
                }
            }
            viaTransfer.removeAll(direct);
            OWLNamedIndividual route1 = dataFactory.getOWLNamedIndividual(route1Iri);
            for (IRI objIri : viaTransfer) {
                OWLNamedIndividual obj = dataFactory.getOWLNamedIndividual(objIri);
                OWLAxiom ax = dataFactory.getOWLObjectPropertyAssertionAxiom(reachesViaProp, route1, obj);
                if (!ontology.containsAxiom(ax)) { manager.addAxiom(ontology, ax); count++; }
            }
        }

        // === Rule 4: connectsObjects ===
        for (Map.Entry<IRI, Set<IRI>> e : routeToObjectsMap.entrySet()) {
            OWLNamedIndividual route = dataFactory.getOWLNamedIndividual(e.getKey());
            for (IRI objIri : e.getValue()) {
                OWLNamedIndividual obj = dataFactory.getOWLNamedIndividual(objIri);
                OWLAxiom ax = dataFactory.getOWLObjectPropertyAssertionAxiom(connectsObjProp, route, obj);
                if (!ontology.containsAxiom(ax)) { manager.addAxiom(ontology, ax); count++; }
            }
        }

        log.info("materializeInferences: added {} total axioms", count);
        return count;
    }

    /**
     * Remove all assertions of the given object property for the given subject individual.
     */
    public synchronized void removeObjectProperty(String subjectId, String propertyName) {
        OWLNamedIndividual subject = dataFactory.getOWLNamedIndividual(iri(subjectId));
        OWLObjectProperty prop = getObjectProperty(propertyName);
        Set<OWLAxiom> toRemove = new HashSet<>(ontology.getObjectPropertyAssertionAxioms(subject));
        toRemove.removeIf(ax -> !((OWLObjectPropertyAssertionAxiom) ax).getProperty().equals(prop));
        // Cast properly
        Set<OWLAxiom> filtered = new HashSet<>();
        for (OWLAxiom ax : ontology.getObjectPropertyAssertionAxioms(subject)) {
            if (ax instanceof OWLObjectPropertyAssertionAxiom opa && opa.getProperty().equals(prop)) {
                filtered.add(ax);
            }
        }
        filtered.forEach(ax -> manager.removeAxiom(ontology, ax));
    }

    public synchronized void removeDataProperty(String subjectId, String propertyName) {
        OWLNamedIndividual subject = dataFactory.getOWLNamedIndividual(iri(subjectId));
        OWLDataProperty prop = getDataProperty(propertyName);
        Set<OWLAxiom> filtered = new HashSet<>();
        for (OWLAxiom ax : ontology.getDataPropertyAssertionAxioms(subject)) {
            if (ax instanceof OWLDataPropertyAssertionAxiom dpa && dpa.getProperty().equals(prop)) {
                filtered.add(ax);
            }
        }
        filtered.forEach(ax -> manager.removeAxiom(ontology, ax));
    }

    // ===== HELPERS =====

    public Optional<String> getDataPropertyValue(OWLNamedIndividual ind, String propertyName) {
        OWLDataProperty prop = getDataProperty(propertyName);
        List<String> values = ontology.getDataPropertyAssertionAxioms(ind).stream()
            .filter(ax -> ax.getProperty().equals(prop))
            .map(ax -> ax.getObject().getLiteral())
            .toList();
        return values.stream().filter(v -> v != null && !v.isBlank()).findFirst()
            .or(() -> values.stream().findFirst());
    }

    public String fragmentOf(OWLNamedIndividual ind) {
        String frag = ind.getIRI().getFragment();
        if (frag == null || frag.isBlank()) {
            return ind.getIRI().toString().replaceAll(".*[#/]", "");
        }
        return frag;
    }

    public boolean individualExists(String id) {
        return ontology.containsIndividualInSignature(iri(id));
    }
}
