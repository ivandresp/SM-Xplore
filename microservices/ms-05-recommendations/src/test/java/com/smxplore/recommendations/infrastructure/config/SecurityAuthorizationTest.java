package com.smxplore.recommendations.infrastructure.config;

import com.smxplore.recommendations.infrastructure.adapters.in.web.error.SecurityProblemWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * MS05-12: la cadena de seguridad niega por defecto cualquier ruta no declarada.
 *
 * <p>Se levanta un contexto Spring Boot mínimo (sin escaneo de componentes ni acceso a Neo4j,
 * Redis o Kafka) con la {@link SecurityConfiguration} real y un controlador de prueba que expone
 * las rutas del servicio más una ruta no listada ({@code /api/v1/unmapped}). Con
 * {@code anyExchange().authenticated()} esa ruta habría aceptado a un turista autenticado; con
 * {@code anyExchange().denyAll()} debe responder 403.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = SecurityAuthorizationTest.TestApplication.class,
        properties = "spring.main.web-application-type=reactive")
class SecurityAuthorizationTest {

    @Value("${local.server.port}")
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + port)
                .build();
    }

    @Test
    void rejectsAnonymousRequestsToAnAuthenticatedRoute() {
        client.get().uri("/api/v1/recommendations")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void allowsTouristsToQueryRecommendations() {
        client.get().uri("/api/v1/recommendations")
                .header(HttpHeaders.AUTHORIZATION, bearer("TOURIST"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void rejectsRolesThatTheRouteDoesNotDeclare() {
        client.get().uri("/api/v1/recommendations")
                .header(HttpHeaders.AUTHORIZATION, bearer("GESTOR"))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void keepsAdministratorsOutOfTouristOnlyPreferences() {
        client.get().uri("/api/v1/preferences")
                .header(HttpHeaders.AUTHORIZATION, bearer("TOURIST"))
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/v1/preferences")
                .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void keepsAdministratorsOutOfTouristOnlyBulkPreferenceReplacement() {
        client.put().uri("/api/v1/preferences")
                .header(HttpHeaders.AUTHORIZATION, bearer("TOURIST"))
                .exchange()
                .expectStatus().isOk();
        client.put().uri("/api/v1/preferences")
                .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deniesRoutesThatAreNotDeclaredExplicitly() {
        client.get().uri("/api/v1/unmapped")
                .header(HttpHeaders.AUTHORIZATION, bearer("TOURIST"))
                .exchange()
                .expectStatus().isForbidden();

        client.get().uri("/api/v1/unmapped")
                .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void restrictsTheInternalAdministrationEndpointToAdministrators() {
        client.post().uri("/internal/admin/reindex")
                .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN"))
                .exchange()
                .expectStatus().isOk();
        client.post().uri("/internal/admin/reindex")
                .header(HttpHeaders.AUTHORIZATION, bearer("TOURIST"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void doesNotExposeMetricsWithoutAnAdministratorRole() {
        client.get().uri("/actuator/metrics")
                .exchange()
                .expectStatus().isUnauthorized();
        client.get().uri("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, bearer("TOURIST"))
                .exchange()
                .expectStatus().isForbidden();
    }

    private static String bearer(String role) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
                (role + "|" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        return "Bearer " + header + "." + payload + ".signature";
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableWebFluxSecurity
    @Import({SecurityConfiguration.class, SecurityProblemWriter.class})
    static class TestApplication {

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties();
        }

        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        /**
         * Token de prueba con forma de JWT ({@code header.payload.signature}) cuyo payload codifica
         * {@code ROLE|uuid}. Es necesario que parezca un JWT porque el {@code DefaultBearerTokenResolver}
         * rechaza el header antes de llegar al decoder si no lo es.
         */
        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return token -> {
                String[] parts = token.split("\\.");
                if (parts.length < 2) {
                    return Mono.error(new BadJwtException("token must look like a JWT"));
                }
                String payload;
                try {
                    payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException exception) {
                    return Mono.error(new BadJwtException("payload is not base64url"));
                }
                int separator = payload.indexOf('|');
                if (separator < 0) {
                    return Mono.error(new BadJwtException("payload must look like ROLE|uuid"));
                }
                String role = payload.substring(0, separator);
                UUID userId = UUID.fromString(payload.substring(separator + 1));
                return Mono.just(Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject(userId.toString())
                        .claim("role", role)
                        .claim("name", "Ana")
                        .build());
            };
        }

        @Bean
        DummyController dummyController() {
            return new DummyController();
        }
    }

    @RestController
    static class DummyController {

        @GetMapping("/api/v1/recommendations")
        Mono<Map<String, Object>> recommendations() {
            return Mono.just(Map.of("items", 0));
        }

        @GetMapping("/api/v1/preferences")
        Mono<Map<String, Object>> preferences() {
            return Mono.just(Map.of("categories", 0));
        }

        @PostMapping("/api/v1/preferences")
        Mono<Map<String, Object>> addPreference() {
            return Mono.just(Map.of("ok", true));
        }

        @PutMapping("/api/v1/preferences")
        Mono<Map<String, Object>> replacePreferences() {
            return Mono.just(Map.of("ok", true));
        }

        @DeleteMapping("/api/v1/preferences/{categoryId}")
        Mono<Void> removePreference(@PathVariable UUID categoryId) {
            return Mono.empty();
        }

        @PostMapping("/api/v1/interactions/view/{itemType}/{itemId}")
        Mono<Void> registerView(@PathVariable String itemType, @PathVariable UUID itemId) {
            return Mono.empty();
        }

        @PostMapping("/internal/admin/reindex")
        Mono<Map<String, Object>> reindex() {
            return Mono.just(Map.of("ok", true));
        }

        @GetMapping("/api/v1/unmapped")
        Mono<Map<String, Object>> unmapped() {
            return Mono.just(Map.of("ok", true));
        }
    }
}
