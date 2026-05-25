package ru.vkr.transport.controller;

import org.springframework.web.bind.annotation.*;
import ru.vkr.transport.importer.OsmImporter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final OsmImporter osmImporter;

    public ImportController(OsmImporter osmImporter) {
        this.osmImporter = osmImporter;
    }

    /**
     * POST /api/import/osm?bbox=59.9,30.2,60.1,30.5
     * Import routes from OpenStreetMap Overpass API
     */
    @PostMapping("/osm")
    public Map<String, Object> importFromOsm(
            @RequestParam(required = false) String bbox,
            @RequestParam(required = false, defaultValue = "5") int limit) {
        return osmImporter.importFromOsm(bbox, limit);
    }

    /**
     * GET /api/import/status
     * Status of the last import
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return osmImporter.getStatus();
    }

    /**
     * POST /api/import/osm-file?path=/tmp/osm_routes.json&limit=5
     * Import from a local Overpass JSON file (useful when API is slow)
     */
    @PostMapping("/osm-file")
    public Map<String, Object> importFromFile(
            @RequestParam String path,
            @RequestParam(required = false, defaultValue = "5") int limit) {
        try {
            String json = Files.readString(Paths.get(path));
            return osmImporter.importFromJson(json, limit);
        } catch (Exception e) {
            return Map.of("error", "Cannot read file: " + e.getMessage());
        }
    }

    /**
     * POST /api/import/fix-links
     * Fix standsNear links for city objects to real OSM stops,
     * and set transferTo for transfer stops (stops on multiple routes).
     */
    @PostMapping("/fix-links")
    public Map<String, Object> fixLinks() {
        return osmImporter.fixLinks();
    }

    /**
     * POST /api/import/object
     * Manually add a city object
     */
    @PostMapping("/object")
    public Map<String, Object> addCityObject(@RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "Unknown");
        String type = (String) body.getOrDefault("type", "CityObject");
        String category = (String) body.getOrDefault("category", "other");
        double lat = ((Number) body.getOrDefault("latitude", 0.0)).doubleValue();
        double lon = ((Number) body.getOrDefault("longitude", 0.0)).doubleValue();
        String nearStopId = (String) body.get("nearStopId");
        return osmImporter.addCityObject(name, type, category, lat, lon, nearStopId);
    }
}
