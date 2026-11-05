package com.smxplore.recommendations;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.neo4j.Neo4jContainer;

/**
 * One singleton Neo4j container for the whole test JVM. Starting a static
 * container in the base class with @Container makes Testcontainers restart it
 * per test class, while Spring keeps a cached context pointing to the old URI.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractNeo4jIntegrationTest {

    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5.26-community")
            .withAdminPassword("test-password");

    static {
        NEO4J.start();
    }

    @DynamicPropertySource
    static void neo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "test-password");
    }
}
