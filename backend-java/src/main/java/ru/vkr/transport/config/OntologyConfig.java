package ru.vkr.transport.config;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.InputStream;

/**
 * Конфигурация OWL. Все OWL-объекты создаются через OWLManager.createOWLOntologyManager()
 * и оборачиваются в OntologyBundle, чтобы избежать конфликта Spring DI с OWL API
 * internal injection points (OWLOntologyManagerImpl использует @Inject).
 */
@Configuration
public class OntologyConfig {

    private static final Logger log = LoggerFactory.getLogger(OntologyConfig.class);

    @Value("${ontology.file}")
    private Resource ontologyFile;

    /**
     * Единый Bundle-bean, создаваемый вне Spring DI для OWL-объектов.
     * Spring не будет пытаться autowire-ить внутренности OWL API.
     */
    @Bean
    public OntologyBundle ontologyBundle() throws Exception {
        log.info("Loading ontology from {}", ontologyFile);
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology;
        try (InputStream is = ontologyFile.getInputStream()) {
            ontology = manager.loadOntologyFromOntologyDocument(is);
        }
        log.info("Ontology loaded: {} axioms", ontology.getAxiomCount());

        log.info("Initializing HermiT reasoner...");
        OWLReasonerFactory reasonerFactory = new ReasonerFactory();
        OWLReasoner reasoner = reasonerFactory.createReasoner(ontology);
        reasoner.precomputeInferences();
        log.info("Reasoner initialized. Consistent: {}", reasoner.isConsistent());

        return new OntologyBundle(manager, ontology, reasoner);
    }

    // НЕ регистрируем OWLOntologyManager, OWLOntology, OWLReasoner, OWLDataFactory
    // как отдельные Spring beans — это вызывает попытку Spring autowire OWLOntologyManagerImpl.
    // Вместо этого все зависимые бины получают OntologyBundle напрямую.

    public static class OntologyBundle {
        private final OWLOntologyManager manager;
        private final OWLOntology ontology;
        private final OWLReasoner reasoner;

        public OntologyBundle(OWLOntologyManager manager, OWLOntology ontology, OWLReasoner reasoner) {
            this.manager = manager;
            this.ontology = ontology;
            this.reasoner = reasoner;
        }

        public OWLOntologyManager getManager() { return manager; }
        public OWLOntology getOntology() { return ontology; }
        public OWLReasoner getReasoner() { return reasoner; }
    }
}
