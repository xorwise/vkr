package ru.vkr.transport.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.vkr.transport.importer.OsmImporter;
import ru.vkr.transport.ontology.OntologyService;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * При старте приложения:
 * 1. Загружает маршруты Петроградского района СПб из OSM (файл или Overpass API)
 * 2. Добавляет реальные городские объекты района
 * 3. Привязывает объекты к ближайшим OSM-остановкам (standsNear) и строит transferTo
 * 4. Материализует выводимые свойства (reachesObject, reachesObjectViaTransfer, connectsObjects)
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final OntologyService svc;
    private final OsmImporter osmImporter;

    @Value("${osm.local.file:}")
    private String localOsmFile;

    @Value("${osm.import.limit-per-type:5}")
    private int limitPerType;

    public DataInitializer(OntologyService svc, OsmImporter osmImporter) {
        this.svc = svc;
        this.osmImporter = osmImporter;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Если маршруты уже есть — пропускаем
        if (!svc.getAllRoutes(null).isEmpty()) {
            log.info("Ontology already has {} routes, skipping initialization", svc.getAllRoutes(null).size());
            return;
        }

        log.info("=== Starting automatic OSM import (Petrogradsky district, SPb) ===");

        // === 1. Импорт маршрутов из OSM ===
        Map<String, Object> result;
        if (localOsmFile != null && !localOsmFile.isBlank() && Files.exists(Paths.get(localOsmFile))) {
            log.info("Loading OSM data from local file: {}", localOsmFile);
            try {
                String json = Files.readString(Paths.get(localOsmFile));
                result = osmImporter.importFromJson(json, limitPerType);
            } catch (Exception e) {
                log.error("Failed to read local OSM file {}: {}", localOsmFile, e.getMessage());
                result = osmImporter.importFromOsm(null, limitPerType);
            }
        } else {
            log.info("No local file configured, fetching from Overpass API...");
            result = osmImporter.importFromOsm(null, limitPerType);
        }

        if (result.containsKey("error")) {
            log.error("OSM import failed: {}", result.get("error"));
            return;
        }
        log.info("OSM import done: {} routes, {} stops",
            result.get("importedRoutes"), result.get("importedStops"));

        // === 2. Добавляем городские объекты Петроградского района ===
        log.info("Adding Petrogradsky district city objects...");
        addCityObjects();
        svc.flushReasoner();

        // === 3. Привязываем объекты к реальным OSM-остановкам + строим transferTo ===
        log.info("Running fix-links (standsNear + transferTo)...");
        Map<String, Object> fixResult = osmImporter.fixLinks();
        log.info("fix-links: {}", fixResult.get("message"));

        // === 4. Материализуем reachesObject / reachesObjectViaTransfer / connectsObjects ===
        log.info("Materializing inferences...");
        int materialized = svc.materializeInferences();
        log.info("Materialized {} inferred axioms", materialized);

        log.info("=== Initialization complete: {} stops, {} routes, {} objects ===",
            svc.getAllStops().size(),
            svc.getAllRoutes(null).size(),
            svc.getAllObjects(null).size());
    }

    /**
     * Добавляет реальные городские объекты Петроградского района СПб.
     * Координаты взяты из OSM/Яндекс.Карт.
     * standsNear будет установлен через fixLinks() к ближайшей OSM-остановке.
     */
    private void addCityObjects() {
        // Медицинские учреждения
        addObj("PSPbGMU_Hospital", "Hospital",
            "ПСПбГМУ им. И.П. Павлова", 59.9640, 30.3170);
        addObj("Center_Med_Polyclinic", "Polyclinic",
            "Центр медицинской профилактики", 59.9635, 30.3238);

        // Образование
        addObj("LETI_University", "University",
            "СПбГЭТУ ЛЭТИ", 59.9725, 30.3168);
        addObj("RGUP_University", "University",
            "ФГБОУВО РГУП", 59.9660, 30.3175);
        addObj("School_67", "School",
            "Гимназия №67", 59.9700, 30.3210);
        addObj("School_82", "School",
            "Лицей №82", 59.9622, 30.3205);

        // Торговля
        addObj("Artvill_Store", "Store",
            "Артвилль", 59.9659, 30.3100);
        addObj("Dom_Mod_Store", "ShoppingCenter",
            "Дом Мод", 59.9670, 30.3118);
        addObj("Dixy_Store", "Store",
            "Дикси", 59.9685, 30.3162);

        // Рекреация
        addObj("Petrogradsky_Park", "Park",
            "Петровский парк", 59.9650, 30.3050);
        addObj("Kronverksky_Garden", "Garden",
            "Кронверкский сад", 59.9670, 30.3190);
    }

    private void addObj(String id, String className, String name, double lat, double lon) {
        if (svc.individualExists(id)) return; // не дублируем
        svc.addIndividualToClass(id, className);
        svc.setDataProperty(id, "objectName", name);
        svc.setDataPropertyDouble(id, "latitude", lat);
        svc.setDataPropertyDouble(id, "longitude", lon);
    }
}
