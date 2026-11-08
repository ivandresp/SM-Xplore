package com.smxplore.recommendations.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for MS05-10: the service-owned compose file must not expose the API on the LAN
 * nor ship insecure defaults that a copy/paste would inherit.
 */
class ServiceComposeDefaultsTest {

    private static String compose() throws Exception {
        return Files.readString(Path.of("docker-compose.yml"));
    }

    @Test
    void publishesTheApiOnLoopbackOnly() throws Exception {
        assertThat(compose()).contains("127.0.0.1:${APP_PORT:-8085}:8080");
    }

    @Test
    void requiresAnExplicitNeo4jPassword() throws Exception {
        assertThat(compose()).contains("${NEO4J_PASSWORD:?set NEO4J_PASSWORD}");
    }

    @Test
    void doesNotShipInsecureOptInDefaults() throws Exception {
        String compose = compose();
        assertThat(compose).contains("${JWT_ALLOW_INSECURE_JWKS:-false}");
        assertThat(compose).contains("${SEED_ENABLED:-false}");
        assertThat(compose).contains("${NEO4J_INITIALIZE_SCHEMA:-false}");
        assertThat(compose).contains("${SPRINGDOC_API_DOCS_ENABLED:-false}");
        assertThat(compose).contains("${SPRINGDOC_SWAGGER_UI_ENABLED:-false}");
    }
}
