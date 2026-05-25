package ru.vkr.transport.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.vkr.transport.ontology.SparqlService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final SparqlService sparqlService;

    public QueryController(SparqlService sparqlService) {
        this.sparqlService = sparqlService;
    }

    /**
     * GET /api/query/routes-reaching?object={id}
     * Маршруты, напрямую достигающие объекта
     */
    @GetMapping("/routes-reaching")
    public List<Map<String, String>> routesReaching(@RequestParam String object) {
        return sparqlService.getRoutesReachingObject(object);
    }

    /**
     * GET /api/query/routes-reaching-via-transfer?object={id}
     * Маршруты, достигающие объекта через пересадку
     */
    @GetMapping("/routes-reaching-via-transfer")
    public List<Map<String, String>> routesReachingViaTransfer(@RequestParam String object) {
        return sparqlService.getRoutesReachingViaTransfer(object);
    }

    /**
     * GET /api/query/routes-reaching-via-transfer-detail?object={id}
     * Маршруты с пересадкой — с деталями: остановка пересадки и финальный маршрут
     */
    @GetMapping("/routes-reaching-via-transfer-detail")
    public List<Map<String, String>> routesReachingViaTransferDetail(@RequestParam String object) {
        return sparqlService.getRoutesReachingViaTransferDetail(object);
    }

    /**
     * GET /api/query/routes-connecting?from={id}&to={id}
     * Маршруты, связывающие два объекта
     */
    @GetMapping("/routes-connecting")
    public List<Map<String, String>> routesConnecting(
            @RequestParam String from,
            @RequestParam String to) {
        return sparqlService.getRoutesConnecting(from, to);
    }

    /**
     * GET /api/query/objects-reachable?stop={id}
     * Объекты, доступные с остановки напрямую
     */
    @GetMapping("/objects-reachable")
    public List<Map<String, String>> objectsReachable(@RequestParam String stop) {
        return sparqlService.getObjectsReachableFromStop(stop);
    }

    /**
     * GET /api/query/objects-reachable-via-transfer?stop={id}
     * Объекты, доступные с остановки через пересадку
     */
    @GetMapping("/objects-reachable-via-transfer")
    public List<Map<String, String>> objectsReachableViaTransfer(@RequestParam String stop) {
        return sparqlService.getObjectsReachableViaTransfer(stop);
    }

    /**
     * GET /api/query/can-reach?route={id}&object={id}
     * Можно ли доехать до объекта по маршруту? (SPARQL ASK)
     */
    @GetMapping("/can-reach")
    public Map<String, Object> canReach(
            @RequestParam String route,
            @RequestParam String object) {
        boolean result = sparqlService.canReach(route, object);
        return Map.of("canReach", result, "route", route, "object", object);
    }

    /**
     * GET /api/query/transfer-stops-between?route1={id}&route2={id}
     * Общие остановки двух маршрутов
     */
    @GetMapping("/transfer-stops-between")
    public List<Map<String, String>> transferStopsBetween(
            @RequestParam String route1,
            @RequestParam String route2) {
        return sparqlService.getTransferStopsBetweenRoutes(route1, route2);
    }

    /**
     * GET /api/query/medical-facilities
     * Все медицинские учреждения
     */
    @GetMapping("/medical-facilities")
    public List<Map<String, String>> medicalFacilities() {
        return sparqlService.getMedicalFacilities();
    }

    /**
     * GET /api/query/educational-facilities
     * Все образовательные учреждения
     */
    @GetMapping("/educational-facilities")
    public List<Map<String, String>> educationalFacilities() {
        return sparqlService.getEducationalFacilities();
    }

    /**
     * GET /api/query/transfer-stops
     * Все пересадочные остановки (через reasoning)
     */
    @GetMapping("/transfer-stops")
    public List<Map<String, String>> transferStops() {
        return sparqlService.getTransferStops();
    }
}
