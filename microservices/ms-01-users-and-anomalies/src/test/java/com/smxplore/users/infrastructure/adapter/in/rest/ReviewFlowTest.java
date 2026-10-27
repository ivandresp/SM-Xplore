package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "app.bootstrap-admin.enabled=true",
            "app.bootstrap-admin.email=admin@test.local",
            "app.bootstrap-admin.password=ChangeMe123!",
            "app.bootstrap-admin.full-name=Admin Test",
            "app.reviews.index-initialization.enabled=true"
        })
@Testcontainers
@EnabledIfDockerAvailable
class ReviewFlowTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "ChangeMe123!";
    private static final String PASSWORD = "ChangeMe123!";
    private static final FakeLittleLlm LITTLE_LLM = FakeLittleLlm.start();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add(
                "spring.r2dbc.url",
                () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/"
                        + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.mongodb.uri", () -> MONGO.getConnectionString() + "/smxplore_reviews");
        registry.add("app.reviews.ai-filter.enabled", () -> "true");
        registry.add("app.reviews.ai-filter.base-url", LITTLE_LLM::baseUrl);
        registry.add("app.reviews.ai-filter.api-key", () -> "test-key");
        registry.add("app.reviews.ai-filter.model", () -> "littlellm-test");
        registry.add("app.reviews.ai-filter.threshold", () -> "0.7");
        registry.add("app.reviews.ai-filter.action", () -> "PENDING_REVIEW");
        registry.add("app.reviews.ai-filter.fail-open", () -> "true");
    }

    @AfterAll
    static void stopFakeLlm() {
        LITTLE_LLM.stop();
    }

    @Value("${local.server.port}")
    private int port;

    private RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsReadsAndSummarizesProviderReviews() {
        String providerId =
                registerProvider("Prestadora Reseñas", "resenas.provider@example.com", "901000111-2", "3010001111");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(reviewProvider(adminToken, providerId, "APROBADO")).isEqualTo(HttpStatus.NO_CONTENT);

        String touristToken =
                registerAndLoginTourist("Turista Reseña", "resenas.tourist@example.com", "7770001001", "3010001001");

        ResponseEntity<Map> created =
                createReview(touristToken, providerId, "PRESTADORA_SERVICIOS", 4, "Excelente servicio");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("rating")).isEqualTo(4);
        assertThat(created.getBody().get("targetType")).isEqualTo("PRESTADORA_SERVICIOS");

        HttpStatusCode duplicate = createReviewStatus(touristToken, providerId, "PRESTADORA_SERVICIOS", 5, "Otra");
        assertThat(duplicate).isEqualTo(HttpStatus.CONFLICT);

        Map<?, ?> page = get("/api/v1/public/reviews?targetType=PRESTADORA_SERVICIOS&targetId=" + providerId);
        assertThat((List<Map<String, Object>>) page.get("content")).hasSize(1);

        Map<?, ?> summary =
                get("/api/v1/public/reviews/summary?targetType=PRESTADORA_SERVICIOS&targetId=" + providerId);
        assertThat(summary.get("reviewCount")).isEqualTo(1);
        assertThat(summary.get("averageRating")).isEqualTo(4.0);
        assertThat(((Map<String, Object>) summary.get("distribution")).get("4")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void providerRespondsAndAdminModeratesReportedReview() {
        String providerId =
                registerProvider("Prestadora Respuesta", "respuesta.provider@example.com", "901000222-3", "3010002222");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(reviewProvider(adminToken, providerId, "APROBADO")).isEqualTo(HttpStatus.NO_CONTENT);

        String touristToken = registerAndLoginTourist(
                "Turista Reportero", "respuesta.tourist@example.com", "7770002002", "3010002002");
        ResponseEntity<Map> created =
                createReview(touristToken, providerId, "PRESTADORA_SERVICIOS", 2, "No fue lo esperado");
        String reviewId = String.valueOf(created.getBody().get("id"));

        String providerToken = login("respuesta.provider@example.com", PASSWORD);
        ResponseEntity<Map> response = respond(providerToken, reviewId, "Gracias por el comentario");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) response.getBody().get("response")).get("text")).isEqualTo("Gracias por el comentario");

        assertThat(respond(touristToken, reviewId, "No debería poder").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(report(touristToken, reviewId, "Lenguaje ofensivo")).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(report(touristToken, reviewId, "Otra vez")).isEqualTo(HttpStatus.CONFLICT);

        Map<?, ?> reported = getWithToken(adminToken, "/api/v1/admin/reviews/reported");
        assertThat((List<Map<String, Object>>) reported.get("content")).hasSize(1);

        ResponseEntity<Map> hidden = moderate(adminToken, reviewId, "HIDE", "Ofensiva");
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(hidden.getBody().get("moderationStatus")).isEqualTo("HIDDEN");

        Map<?, ?> visiblePage = get("/api/v1/public/reviews?targetType=PRESTADORA_SERVICIOS&targetId=" + providerId);
        assertThat((List<Map<String, Object>>) visiblePage.get("content")).isEmpty();

        ResponseEntity<Map> restored = moderate(adminToken, reviewId, "KEEP", "Revisada");
        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restored.getBody().get("moderationStatus")).isEqualTo("VISIBLE");

        Map<?, ?> afterRestore = get("/api/v1/public/reviews?targetType=PRESTADORA_SERVICIOS&targetId=" + providerId);
        assertThat((List<Map<String, Object>>) afterRestore.get("content")).hasSize(1);
    }

    @Test
    void requiresApprovedTargetAndTouristRole() {
        String pendingProviderId =
                registerProvider("Prestadora Pendiente", "pendiente.review@example.com", "901000333-4", "3010003333");
        String touristToken = registerAndLoginTourist(
                "Turista Validación", "validacion.tourist@example.com", "7770003003", "3010003003");

        assertThat(createReviewStatus(touristToken, pendingProviderId, "PRESTADORA_SERVICIOS", 5, "Aún no aprobada"))
                .isEqualTo(HttpStatus.BAD_REQUEST);

        String providerToken = login("pendiente.review@example.com", PASSWORD);
        assertThat(createReviewStatus(providerToken, pendingProviderId, "PRESTADORA_SERVICIOS", 5, "No soy turista"))
                .isEqualTo(HttpStatus.FORBIDDEN);

        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String guideId = createGuide(adminToken, "guia.review@example.com", "7770004004", "LIC-REVIEW");
        assertThat(createReviewStatus(touristToken, guideId, "GUIA", 5, "Excelente guía"))
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aiFilterQueuesReviewBombingForModeration() {
        String providerId = registerProvider("Prestadora IA", "ia.provider@example.com", "901000444-5", "3010004444");
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(reviewProvider(adminToken, providerId, "APROBADO")).isEqualTo(HttpStatus.NO_CONTENT);

        String touristToken =
                registerAndLoginTourist("Turista IA", "ia.tourist@example.com", "7770005005", "3010005005");

        ResponseEntity<Map> created = createReview(
                touristToken,
                providerId,
                "PRESTADORA_SERVICIOS",
                5,
                "BOMB: todos deben comprar aquí, la mejor agencia de la ciudad!!!");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("moderationStatus")).isEqualTo("PENDIENTE_REVISION");
        String reviewId = String.valueOf(created.getBody().get("id"));

        Map<?, ?> publicPage = get("/api/v1/public/reviews?targetType=PRESTADORA_SERVICIOS&targetId=" + providerId);
        assertThat((List<Map<String, Object>>) publicPage.get("content")).isEmpty();

        Map<?, ?> flagged = getWithToken(adminToken, "/api/v1/admin/reviews/ai-flagged");
        List<Map<String, Object>> flaggedContent = (List<Map<String, Object>>) flagged.get("content");
        assertThat(flaggedContent).hasSize(1);
        assertThat(((Map<String, Object>) flaggedContent.get(0).get("review")).get("id"))
                .isEqualTo(reviewId);
        Map<String, Object> ai = (Map<String, Object>) flaggedContent.get(0).get("ai");
        assertThat(ai.get("flagged")).isEqualTo(true);
        assertThat(((Number) ai.get("score")).doubleValue()).isEqualTo(0.93);
        assertThat((List<String>) ai.get("categories")).contains("COORDINADA");

        ResponseEntity<Map> kept = moderate(adminToken, reviewId, "KEEP", "Campaña revisada");
        assertThat(kept.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(kept.getBody().get("moderationStatus")).isEqualTo("VISIBLE");

        Map<?, ?> after = get("/api/v1/public/reviews?targetType=PRESTADORA_SERVICIOS&targetId=" + providerId);
        assertThat((List<Map<String, Object>>) after.get("content")).hasSize(1);

        Map<?, ?> clearedQueue = getWithToken(adminToken, "/api/v1/admin/reviews/ai-flagged");
        assertThat((List<Map<String, Object>>) clearedQueue.get("content")).isEmpty();
    }

    private String createGuide(String token, String email, String dni, String license) {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of(
                        "role",
                        "GUIA",
                        "fullName",
                        "Guía Reseña",
                        "email",
                        email,
                        "dniType",
                        "CC",
                        "dniNumber",
                        dni,
                        "license",
                        license))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return String.valueOf(response.getBody().get("id"));
    }

    private String registerAndLoginTourist(String name, String email, String dni, String phone) {
        ResponseEntity<Map> registration = client.post()
                .uri("/api/v1/auth/register/turista")
                .body(Map.of(
                        "fullName", name,
                        "email", email,
                        "phone", phone,
                        "password", PASSWORD,
                        "dniType", "CC",
                        "dniNumber", dni,
                        "preferredLanguage", "es",
                        "nationality", "Colombia"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return login(email, PASSWORD);
    }

    private String registerProvider(String name, String email, String nit, String phone) {
        ResponseEntity<Map> registration = client.post()
                .uri("/api/v1/auth/register/prestadora")
                .body(Map.of(
                        "fullName", name,
                        "email", email,
                        "phone", phone,
                        "password", PASSWORD,
                        "nit", nit,
                        "businessType", "Agencia de viajes",
                        "websiteUrl", "https://prestadora.example.com"))
                .retrieve()
                .toEntity(Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return String.valueOf(registration.getBody().get("id"));
    }

    private String login(String email, String password) {
        ResponseEntity<Map> response = client.post()
                .uri("/api/v1/auth/login")
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .toEntity(Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return String.valueOf(response.getBody().get("accessToken"));
    }

    private HttpStatusCode reviewProvider(String token, String userId, String decision) {
        return client.post()
                .uri("/api/v1/admin/users/" + userId + "/provider-review")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("decision", decision))
                .exchange((request, response) -> response.getStatusCode());
    }

    private ResponseEntity<Map> createReview(
            String token, String targetId, String targetType, int rating, String comment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        body.put("rating", rating);
        body.put("comment", comment);
        return client.post()
                .uri("/api/v1/reviews")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .retrieve()
                .toEntity(Map.class);
    }

    private HttpStatusCode createReviewStatus(
            String token, String targetId, String targetType, int rating, String comment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        body.put("rating", rating);
        body.put("comment", comment);
        return client.post()
                .uri("/api/v1/reviews")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(body)
                .exchange((request, response) -> response.getStatusCode());
    }

    private ResponseEntity<Map> respond(String token, String reviewId, String text) {
        return client.post()
                .uri("/api/v1/reviews/" + reviewId + "/response")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("text", text))
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), (request, response) -> {})
                .toEntity(Map.class);
    }

    private HttpStatusCode report(String token, String reviewId, String reason) {
        return client.post()
                .uri("/api/v1/reviews/" + reviewId + "/report")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("reason", reason))
                .exchange((request, response) -> response.getStatusCode());
    }

    private ResponseEntity<Map> moderate(String token, String reviewId, String decision, String reason) {
        return client.patch()
                .uri("/api/v1/admin/reviews/" + reviewId + "/moderation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(Map.of("decision", decision, "reason", reason))
                .retrieve()
                .toEntity(Map.class);
    }

    private Map<?, ?> get(String uri) {
        return client.get().uri(uri).retrieve().toEntity(Map.class).getBody();
    }

    private Map<?, ?> getWithToken(String token, String uri) {
        return client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(Map.class)
                .getBody();
    }

    /**
     * Servidor HTTP mínimo que imita la API de chat de OpenAI de littleLLM. Marca como bombing cualquier reseña
     * que contenga "BOMB" y exige la llave configurada.
     */
    private static final class FakeLittleLlm {

        private static final String FLAGGED = """
                {"flagged":true,"score":0.93,"categories":["COORDINADA","GENERICA"],\
                "reason":"campaña coordinada de elogios"}""";
        private static final String CLEAN = """
                {"flagged":false,"score":0.04,"categories":[],"reason":"opinión legítima"}""";

        private final HttpServer server;

        private FakeLittleLlm(HttpServer server) {
            this.server = server;
        }

        static FakeLittleLlm start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                FakeLittleLlm fake = new FakeLittleLlm(server);
                server.createContext("/v1/chat/completions", fake::handle);
                server.start();
                return fake;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        void stop() {
            server.stop(0);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean authorized =
                    "Bearer test-key".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            String content = requestBody.contains("BOMB") ? FLAGGED : CLEAN;
            String response =
                    "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + escape(content) + "\"}}]}";

            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(authorized ? 200 : 401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
