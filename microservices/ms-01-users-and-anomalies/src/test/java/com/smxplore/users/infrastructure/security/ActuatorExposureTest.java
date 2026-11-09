package com.smxplore.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Regresión de MS01-14: solo {@code health} e {@code info} son públicos; las métricas y Prometheus exigen autenticación
 * salvo que {@code app.security.actuator.public-metrics} se habilite explícitamente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "management.health.defaults.enabled=false"
        })
class ActuatorExposureTest {

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Test
    void healthIsPubliclyReachable() {
        client.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .value(code -> assertThat(code).isNotIn(401, 403));
    }

    @Test
    void prometheusIsNotPublicByDefault() {
        client.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .value(code -> assertThat(code).isIn(401, 403, 404));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubR2dbcConfig {

        @Bean
        ConnectionFactory connectionFactory() {
            return new ConnectionFactory() {
                @Override
                public Publisher<? extends Connection> create() {
                    return Mono.empty();
                }

                @Override
                public ConnectionFactoryMetadata getMetadata() {
                    return () -> "PostgreSQL";
                }
            };
        }
    }
}
