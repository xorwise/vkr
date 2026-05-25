package ru.vkr.transport.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.vkr.transport.config.OntologyConfig;
import ru.vkr.transport.ontology.OntologyService;

import org.semanticweb.owlapi.model.OWLNamedIndividual;
import ru.vkr.transport.dto.CityObjectDto;
import ru.vkr.transport.dto.RouteDto;
import ru.vkr.transport.dto.StopDto;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OsmImporter {

    private static final Logger log = LoggerFactory.getLogger(OsmImporter.class);

    private final OntologyService ontologyService;
    private final OWLOntology ontology;
    private final OWLOntologyManager manager;
    private final ObjectMapper objectMapper;

    @Value("${overpass.api.url}")
    private String overpassUrl;

    @Value("${overpass.default.bbox}")
    private String defaultBbox;

    public enum ImportStatus { IDLE, RUNNING, SUCCESS, ERROR }

    private final AtomicReference<ImportStatus> status = new AtomicReference<>(ImportStatus.IDLE);
    private volatile String lastMessage = "No import started";
    private volatile int lastImportedRoutes = 0;
    private volatile int lastImportedStops = 0;

    public OsmImporter(OntologyService ontologyService,
                       OntologyConfig.OntologyBundle bundle,
                       ObjectMapper objectMapper) {
        this.ontologyService = ontologyService;
        this.ontology = bundle.getOntology();
        this.manager = bundle.getManager();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status.get().name());
        result.put("message", lastMessage);
        result.put("importedRoutes", lastImportedRoutes);
        result.put("importedStops", lastImportedStops);
        return result;
    }

    public Map<String, Object> importFromOsm(String bbox) {
        return importFromOsm(bbox, 5);
    }

    public Map<String, Object> importFromJson(String json, int limitPerType) {
        if (status.get() == ImportStatus.RUNNING) {
            return Map.of("error", "Import already running");
        }
        status.set(ImportStatus.RUNNING);
        lastMessage = "Starting import from provided JSON...";
        lastImportedRoutes = 0;
        lastImportedStops = 0;
        try {
            List<OsmRoute> allRoutes = parseOverpassResponse(json);
            log.info("Parsed {} total routes from JSON", allRoutes.size());
            List<OsmRoute> routes = limitPerType(allRoutes, limitPerType);
            log.info("Importing {} routes after limiting to {} per type", routes.size(), limitPerType);
            importRoutesToOntology(routes);
            ontologyService.flushReasoner();
            lastImportedRoutes = routes.size();
            lastMessage = String.format("Import successful: %d routes, %d stops", lastImportedRoutes, lastImportedStops);
            status.set(ImportStatus.SUCCESS);
            log.info(lastMessage);
            return Map.of("status", "success", "importedRoutes", lastImportedRoutes, "importedStops", lastImportedStops);
        } catch (Exception e) {
            lastMessage = "Import error: " + e.getMessage();
            status.set(ImportStatus.ERROR);
            log.error("OSM import from JSON failed", e);
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> importFromOsm(String bbox, int limitPerType) {
        if (status.get() == ImportStatus.RUNNING) {
            return Map.of("error", "Import already running");
        }

        status.set(ImportStatus.RUNNING);
        lastMessage = "Starting import...";
        lastImportedRoutes = 0;
        lastImportedStops = 0;

        try {
            String useBbox = (bbox != null && !bbox.isBlank()) ? bbox : defaultBbox;
            log.info("Starting OSM import with bbox: {}, limitPerType={}", useBbox, limitPerType);

            String overpassQuery = buildOverpassQuery(useBbox);
            String response = fetchOverpass(overpassQuery);

            List<OsmRoute> allRoutes = parseOverpassResponse(response);
            log.info("Parsed {} total routes from OSM", allRoutes.size());

            // Limit to N routes per transport type
            List<OsmRoute> routes = limitPerType(allRoutes, limitPerType);
            log.info("Importing {} routes after limiting to {} per type", routes.size(), limitPerType);

            importRoutesToOntology(routes);

            ontologyService.flushReasoner();

            lastImportedRoutes = routes.size();
            lastMessage = String.format("Import successful: %d routes, %d stops",
                lastImportedRoutes, lastImportedStops);
            status.set(ImportStatus.SUCCESS);
            log.info(lastMessage);

            return Map.of(
                "status", "success",
                "importedRoutes", lastImportedRoutes,
                "importedStops", lastImportedStops
            );
        } catch (Exception e) {
            lastMessage = "Import error: " + e.getMessage();
            status.set(ImportStatus.ERROR);
            log.error("OSM import failed", e);
            return Map.of("error", e.getMessage());
        }
    }

    private List<OsmRoute> limitPerType(List<OsmRoute> routes, int maxPerType) {
        Map<String, Integer> countByType = new LinkedHashMap<>();
        List<OsmRoute> result = new ArrayList<>();
        // Prefer routes that have at least 2 stops with coordinates
        for (OsmRoute r : routes) {
            long stopsWithCoords = r.getStops().stream()
                .filter(s -> s.getLat() != 0 || s.getLon() != 0)
                .count();
            if (stopsWithCoords < 2) continue;
            String type = r.getRouteType();
            int count = countByType.getOrDefault(type, 0);
            if (count < maxPerType) {
                result.add(r);
                countByType.put(type, count + 1);
            }
        }
        return result;
    }

    private String buildOverpassQuery(String bbox) {
        return "[out:json][timeout:60];\n" +
               "(\n" +
               "  relation[\"type\"=\"route\"][\"route\"~\"bus|tram|trolleybus|subway\"](" + bbox + ");\n" +
               ");\n" +
               "out body;\n" +
               ">;\n" +
               "out body qt;";
    }

    private String fetchOverpass(String query) throws IOException {
        log.info("Fetching from Overpass API: {}", overpassUrl);
        String encodedData = "data=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
        URL url = new URL(overpassUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent", "TransportOntologyApp/1.0");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(encodedData.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        log.info("Overpass response code: {}", code);
        InputStream is = (code < 400) ? conn.getInputStream() : conn.getErrorStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private List<OsmRoute> parseOverpassResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode elements = root.path("elements");

        // Build node map (osmId -> node)
        Map<Long, JsonNode> nodeMap = new HashMap<>();
        for (JsonNode el : elements) {
            if ("node".equals(el.path("type").asText())) {
                nodeMap.put(el.path("id").asLong(), el);
            }
        }

        List<OsmRoute> routes = new ArrayList<>();
        for (JsonNode el : elements) {
            if (!"relation".equals(el.path("type").asText())) continue;

            JsonNode tags = el.path("tags");
            String routeType = tags.path("route").asText("");
            if (!routeType.matches("bus|tram|trolleybus|subway")) continue;

            OsmRoute route = new OsmRoute();
            route.setOsmId(el.path("id").asLong());
            route.setRef(tags.path("ref").asText("?"));
            route.setRouteType(routeType);

            // OSM routes interleave stop_position + platform nodes for each physical stop.
            // Strategy: use stop_position nodes for geometry (they're on the road),
            // and look ahead/behind for a platform node with a name.
            // We collect (stop_nodes, platform_nodes) in order, then merge.
            List<OsmRoute.OsmStop> stops = new ArrayList<>();
            List<JsonNode> memberNodes = new ArrayList<>();
            List<String> memberRoles = new ArrayList<>();
            for (JsonNode member : el.path("members")) {
                if (!"node".equals(member.path("type").asText())) continue;
                String role = member.path("role").asText("");
                // Accept only exact "stop" and "platform" roles (not entry_only/exit_only)
                // to avoid double-counting bidirectional stops
                if (!role.equals("stop") && !role.equals("platform") &&
                    !role.equals("stop_position")) continue;
                long nodeId = member.path("ref").asLong();
                JsonNode node = nodeMap.get(nodeId);
                if (node == null) continue;
                memberNodes.add(node);
                memberRoles.add(role);
            }

            // Build stop list: prefer stop_position geometry, use platform name when available
            int i = 0;
            while (i < memberNodes.size()) {
                String role = memberRoles.get(i);
                JsonNode node = memberNodes.get(i);

                if (role.contains("stop")) {
                    // This is a stop_position node: use its coords
                    OsmRoute.OsmStop stop = new OsmRoute.OsmStop();
                    stop.setOsmId(node.path("id").asLong());
                    stop.setLat(node.path("lat").asDouble());
                    stop.setLon(node.path("lon").asDouble());

                    // Try to get name from this node or next platform node
                    String name = extractName(node);
                    if (name.isEmpty() && i + 1 < memberNodes.size() && memberRoles.get(i + 1).contains("platform")) {
                        name = extractName(memberNodes.get(i + 1));
                        i++; // consume the platform too
                    }
                    stop.setName(name);
                    stops.add(stop);
                } else {
                    // platform without preceding stop: use platform coords and name
                    OsmRoute.OsmStop stop = new OsmRoute.OsmStop();
                    stop.setOsmId(node.path("id").asLong());
                    stop.setLat(node.path("lat").asDouble());
                    stop.setLon(node.path("lon").asDouble());
                    stop.setName(extractName(node));
                    stops.add(stop);
                }
                i++;
            }
            route.setStops(stops);
            routes.add(route);
        }
        return routes;
    }

    private void importRoutesToOntology(List<OsmRoute> routes) {
        Set<String> addedStops = new HashSet<>();

        for (OsmRoute route : routes) {
            String routeType = capitalizeFirst(route.getRouteType().equals("subway") ? "metro" : route.getRouteType());
            String transportId = routeType + "_osm_" + route.getOsmId();
            String routeId = "Route_" + routeType + "_" + sanitize(route.getRef()) + "_" + route.getOsmId();

            // Add transport individual
            ontologyService.addIndividualToClass(transportId, routeType);
            ontologyService.setDataProperty(transportId, "transportType", routeType);
            ontologyService.setDataProperty(transportId, "osmId", String.valueOf(route.getOsmId()));

            // Add route individual
            ontologyService.addIndividualToClass(routeId, "Route");
            ontologyService.setDataProperty(routeId, "routeRef", route.getRef());
            ontologyService.setDataProperty(routeId, "osmId", String.valueOf(route.getOsmId()));
            ontologyService.setObjectProperty(routeId, "routeTransport", transportId);

            // Add stops and links
            List<OsmRoute.OsmStop> stops = route.getStops();
            for (int i = 0; i < stops.size(); i++) {
                OsmRoute.OsmStop stop = stops.get(i);
                String stopId = "Stop_" + stop.getOsmId();

                if (!addedStops.contains(stopId)) {
                    ontologyService.addIndividualToClass(stopId, "Stop");
                    ontologyService.replaceDataProperty(stopId, "stopName", stop.getName());
                    ontologyService.setDataPropertyDouble(stopId, "latitude", stop.getLat());
                    ontologyService.setDataPropertyDouble(stopId, "longitude", stop.getLon());
                    ontologyService.setDataProperty(stopId, "osmId", String.valueOf(stop.getOsmId()));
                    addedStops.add(stopId);
                    lastImportedStops++;
                }

                // Create StopsLink between consecutive stops
                if (i > 0) {
                    OsmRoute.OsmStop prevStop = stops.get(i - 1);
                    String prevStopId = "Stop_" + prevStop.getOsmId();
                    String slId = "SL_" + route.getOsmId() + "_" + i;

                    ontologyService.addIndividualToClass(slId, "StopsLink");
                    ontologyService.setObjectProperty(slId, "beginStop", prevStopId);
                    ontologyService.setObjectProperty(slId, "endStop", stopId);
                    ontologyService.setDataPropertyInt(slId, "stopOrder", i);
                    ontologyService.setObjectProperty(routeId, "stopLink", slId);
                }
            }
        }
    }

    public Map<String, Object> addCityObject(String name, String type, String category,
                                              double lat, double lon, String nearStopId) {
        try {
            String id = type + "_" + sanitize(name) + "_" + System.currentTimeMillis();
            ontologyService.addIndividualToClass(id, type);
            ontologyService.setDataProperty(id, "objectName", name);
            ontologyService.setDataProperty(id, "objectCategory", category);
            ontologyService.setDataPropertyDouble(id, "latitude", lat);
            ontologyService.setDataPropertyDouble(id, "longitude", lon);

            if (nearStopId != null && !nearStopId.isBlank()) {
                ontologyService.setObjectProperty(id, "standsNear", nearStopId);
            }

            ontologyService.flushReasoner();
            return Map.of("status", "success", "id", id);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Fix standsNear links for city objects (point to nearest real OSM stop)
     * and set transferTo links for stops shared by multiple routes.
     */
    public Map<String, Object> fixLinks() {
        int standsNearFixed = 0;
        int transferToAdded = 0;

        try {
            // === 1. Fix standsNear for city objects ===
            List<CityObjectDto> objects = ontologyService.getAllObjects(null);
            List<StopDto> allStops = ontologyService.getAllStops();

            // Only consider stops with real OSM IDs (imported from OSM) and coordinates
            List<StopDto> osmStops = allStops.stream()
                .filter(s -> s.getOsmId() != null && s.getLatitude() != null && s.getLongitude() != null)
                .toList();

            log.info("Fixing standsNear: {} city objects, {} OSM stops available", objects.size(), osmStops.size());

            for (CityObjectDto obj : objects) {
                Double objLat = obj.getLatitude();
                Double objLon = obj.getLongitude();
                if (objLat == null || objLon == null) continue;

                // Find nearest OSM stop
                StopDto nearest = null;
                double minDist = Double.MAX_VALUE;
                for (StopDto stop : osmStops) {
                    double d = haversine(objLat, objLon, stop.getLatitude(), stop.getLongitude());
                    if (d < minDist) {
                        minDist = d;
                        nearest = stop;
                    }
                }

                if (nearest != null) {
                    // Remove old standsNear by adding the new assertion (OWL API adds, doesn't replace)
                    // We use removeStandsNear first to avoid duplicate links
                    ontologyService.removeObjectProperty(obj.getId(), "standsNear");
                    ontologyService.setObjectProperty(obj.getId(), "standsNear", nearest.getId());
                    log.info("  {} -> {} ({}m)", obj.getId(), nearest.getId(), (int) minDist);
                    standsNearFixed++;
                }
            }

            // === 2. Set transferTo for stops that appear in multiple routes ===
            // Build map: stopId -> list of routeIds
            List<RouteDto> allRoutes = ontologyService.getAllRoutes(null);
            Map<String, List<String>> stopToRoutes = new HashMap<>();
            for (RouteDto route : allRoutes) {
                List<String> stopIds = route.getStopIds();
                if (stopIds == null) continue;
                for (String stopId : stopIds) {
                    stopToRoutes.computeIfAbsent(stopId, k -> new ArrayList<>()).add(route.getId());
                }
            }

            // Remove all existing transferTo links first, then re-add
            for (StopDto stop : allStops) {
                ontologyService.removeObjectProperty(stop.getId(), "transferTo");
            }

            for (Map.Entry<String, List<String>> entry : stopToRoutes.entrySet()) {
                String stopId = entry.getKey();
                List<String> routeIds = entry.getValue();
                if (routeIds.size() >= 2) {
                    for (String routeId : routeIds) {
                        ontologyService.setObjectProperty(stopId, "transferTo", routeId);
                        transferToAdded++;
                    }
                    log.info("  TransferStop {}: {} routes", stopId, routeIds.size());
                }
            }

            ontologyService.flushReasoner();

            String msg = String.format("Fixed %d standsNear links, added %d transferTo links", standsNearFixed, transferToAdded);
            log.info(msg);
            return Map.of("status", "success", "standsNearFixed", standsNearFixed, "transferToAdded", transferToAdded, "message", msg);

        } catch (Exception e) {
            log.error("fixLinks failed", e);
            return Map.of("error", e.getMessage());
        }
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dlat = Math.toRadians(lat2 - lat1);
        double dlon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dlat / 2) * Math.sin(dlat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dlon / 2) * Math.sin(dlon / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    private String extractName(JsonNode node) {
        JsonNode tags = node.path("tags");
        String name = tags.path("name").asText("");
        if (name.isEmpty()) name = tags.path("official_name").asText("");
        if (name.isEmpty()) name = tags.path("ref").asText("");
        return name;
    }

    private String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9а-яА-Я_]", "_");
    }
}
