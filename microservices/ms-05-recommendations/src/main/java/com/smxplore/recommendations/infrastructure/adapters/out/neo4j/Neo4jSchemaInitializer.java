package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.neo4j.initialize-schema", havingValue = "true", matchIfMissing = true)
public class Neo4jSchemaInitializer implements ApplicationRunner {

    private final Driver driver;

    @Override
    public void run(ApplicationArguments args) {
        // Startup-only migration. The request path stays reactive; running this on the
        // Netty event loop would be worse than a short, single-threaded startup task.
        List<String> statements = readStatements("cypher/schema.cypher");
        try (Session session = driver.session()) {
            for (String statement : statements) {
                session.run(statement).consume();
            }
        }
        log.info("Neo4j schema initialized with {} statements", statements.size());
    }

    static List<String> readStatements(String location) {
        try (InputStream input = new ClassPathResource(location).getInputStream()) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return Arrays.stream(content.split(";"))
                    .map(String::trim)
                    .filter(statement -> !statement.isBlank())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read Cypher resource: " + location, exception);
        }
    }
}
