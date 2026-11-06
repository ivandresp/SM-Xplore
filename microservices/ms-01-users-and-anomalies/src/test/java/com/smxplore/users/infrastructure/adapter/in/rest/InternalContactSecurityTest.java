package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.UserSearchCriteria;
import com.smxplore.users.application.dto.UserStatistics;
import com.smxplore.users.application.dto.UserSummaryView;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Mono;

/**
 * Pruebas del endpoint interno contra la cadena de seguridad real, sin Docker: se levanta el contexto con un
 * repositorio de usuarios de prueba y el perfil {@code test} (Flyway y DataSource JDBC desactivados).
 *
 * <p>Comprueban que la ruta responde sin JWT cuando la clave compartida coincide, que sin clave o con una
 * clave distinta devuelve 403, que un identificador inexistente devuelve 404 y que el resto de rutas
 * {@code /internal/**} siguen cerradas (401).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "app.internal.api-key=clave-interna-de-prueba"
        })
class InternalContactSecurityTest {

    private static final String API_KEY = "clave-interna-de-prueba";
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Value("${local.server.port}")
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        StubUserRepository.seed(turista());
    }

    @Test
    void servesTheContactWithoutJwtWhenTheInternalKeyMatches() {
        ResponseEntity<Map> response = request(contactPath(USER_ID), API_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("userId", USER_ID.toString())
                .containsEntry("email", "ada@example.com")
                .containsEntry("fullName", "Ada Turista")
                .containsEntry("role", "TOURIST")
                .containsEntry("phone", "3001234567")
                .containsEntry("active", true);
    }

    @Test
    void rejectsRequestsWithoutTheInternalKey() {
        ResponseEntity<Map> response = request(contactPath(USER_ID), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("code", "INVALID_INTERNAL_API_KEY");
    }

    @Test
    void rejectsRequestsWithAWrongInternalKey() {
        ResponseEntity<Map> response = request(contactPath(USER_ID), "otra-clave");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("code", "INVALID_INTERNAL_API_KEY");
    }

    @Test
    void returnsNotFoundForAnUnknownUser() {
        ResponseEntity<Map> response = request(contactPath(UUID.randomUUID()), API_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "USER_NOT_FOUND");
    }

    @Test
    void keepsTheRestOfInternalRoutesClosed() {
        for (String path : List.of("/internal/users/" + USER_ID, "/internal/users/" + USER_ID + "/something-else")) {
            ResponseEntity<Map> response = request(path, API_KEY);

            assertThat(response.getStatusCode())
                    .as("La ruta %s no debe quedar abierta sin JWT", path)
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).containsEntry("code", "UNAUTHORIZED");
        }
    }

    private ResponseEntity<Map> request(String uri, String apiKey) {
        return client.get()
                .uri(uri)
                .headers(headers -> {
                    if (apiKey != null) {
                        headers.set(InternalContactController.API_KEY_HEADER, apiKey);
                    }
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {})
                .toEntity(Map.class);
    }

    private static String contactPath(UUID userId) {
        return "/internal/users/" + userId + "/contact";
    }

    private static Turista turista() {
        return Turista.register(
                USER_ID,
                "Ada Turista",
                new Email("ada@example.com"),
                "3001234567",
                new PasswordHash("hash-de-prueba"),
                "CC",
                "1234567890",
                "es",
                LocalDate.of(1990, 1, 1),
                "Colombia",
                NOW);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubUserRepositoryConfig {

        @Bean
        @Primary
        UserRepository stubUserRepository() {
            return new StubUserRepository();
        }
    }

    /** Repositorio en memoria: evita PostgreSQL y Docker para estas pruebas de seguridad. */
    static final class StubUserRepository implements UserRepository {

        private static volatile Usuario current;

        static void seed(Usuario user) {
            current = user;
        }

        @Override
        public Mono<Usuario> findById(UUID id) {
            Usuario user = current;
            return user != null && user.id().equals(id) ? Mono.just(user) : Mono.empty();
        }

        @Override
        public Mono<Usuario> findByEmail(Email email) {
            return Mono.empty();
        }

        @Override
        public Mono<Boolean> existsByEmail(Email email) {
            return Mono.just(false);
        }

        @Override
        public Mono<PageResult<UserSummaryView>> search(UserSearchCriteria criteria) {
            return Mono.error(new UnsupportedOperationException("No usado en esta prueba"));
        }

        @Override
        public Mono<Long> countActiveByRole(Role role) {
            return Mono.just(0L);
        }

        @Override
        public Mono<UserStatistics> statistics() {
            return Mono.error(new UnsupportedOperationException("No usado en esta prueba"));
        }

        @Override
        public Mono<Usuario> save(Usuario user) {
            current = user;
            return Mono.just(user);
        }

        @Override
        public Mono<Void> deleteProfile(Role role, UUID userId) {
            return Mono.empty();
        }
    }
}
