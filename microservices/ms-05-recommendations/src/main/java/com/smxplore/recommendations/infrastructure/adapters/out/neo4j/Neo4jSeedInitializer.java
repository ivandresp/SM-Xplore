package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class Neo4jSeedInitializer implements ApplicationRunner {

    private final Driver driver;

    @Override
    public void run(ApplicationArguments args) {
        // Startup-only demo data. It is disabled by default and never used in production.
        List<String> statements = Neo4jSchemaInitializer.readStatements("cypher/seed.cypher");
        try (Session session = driver.session()) {
            for (String statement : statements) {
                session.run(statement).consume();
            }
        }
        log.info("Neo4j seed loaded with {} statements. No RESERVO edges are created.", statements.size());
    }
}
