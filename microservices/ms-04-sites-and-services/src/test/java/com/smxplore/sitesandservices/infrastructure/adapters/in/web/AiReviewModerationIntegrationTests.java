package com.smxplore.sitesandservices.infrastructure.adapters.in.web;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.TestJwtSupport;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReviewResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
class AiReviewModerationIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    private static final HttpServer AI_SERVER = startAiServer();

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @DynamicPropertySource
    static void aiProperties(DynamicPropertyRegistry registry) {
        registry.add("app.reviews.ai-filter.enabled", () -> "true");
        registry.add("app.reviews.ai-filter.base-url",
                () -> "http://127.0.0.1:" + AI_SERVER.getAddress().getPort());
        registry.add("app.reviews.ai-filter.threshold", () -> "0.7");
        registry.add("app.reviews.ai-filter.action", () -> "PENDING_REVIEW");
        registry.add("app.reviews.ai-filter.fail-open", () -> "true");
    }

    @AfterAll
    static void stopAiServer() {
        AI_SERVER.stop(0);
    }

    @Test
    void sendsFlaggedReviewsToTheAdminQueueAndLetsTheAdminPublishThem() {
        UUID adminId = UUID.randomUUID();
        UUID flaggedTourist = UUID.randomUUID();
        UUID cleanTourist = UUID.randomUUID();
        UUID flaggedSiteId = persistPublishedSite("Sitio con campaña").siteId();
        UUID cleanSiteId = persistPublishedSite("Sitio con reseña limpia").siteId();

        ReviewResponse flagged = createReview(
                flaggedTourist, flaggedSiteId, "Campaña coordinada de cinco estrellas");
        ReviewResponse clean = createReview(
                cleanTourist, cleanSiteId, "Una reseña limpia de una visita real");

        assertThat(flagged.status()).isEqualTo(ReviewStatus.PENDING_REVIEW);
        assertThat(flagged.ai()).isNotNull();
        assertThat(flagged.ai().status()).isEqualTo("ASSESSED");
        assertThat(flagged.ai().score()).isEqualTo(0.91);
        assertThat(clean.status()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(clean.ai()).isNotNull();
        assertThat(clean.ai().flagged()).isFalse();

        PageResponse<ReviewResponse> queue = clientFor(adminId, "ADMIN")
                .get().uri("/api/v1/admin/reviews/ai-flagged?page=0&size=50")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PageResponse<ReviewResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(queue).isNotNull();
        assertThat(queue.content())
                .extracting(ReviewResponse::reviewId)
                .contains(flagged.reviewId())
                .doesNotContain(clean.reviewId());

        clientFor(flaggedTourist, "TOURIST")
                .get().uri("/api/v1/admin/reviews/ai-flagged")
                .exchange()
                .expectStatus().isForbidden();

        ReviewResponse published = clientFor(adminId, "ADMIN")
                .patch().uri("/api/v1/reviews/" + flagged.reviewId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"status":"PUBLISHED"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReviewResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(published).isNotNull();
        assertThat(published.status()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(published.ai()).isNotNull();
        assertThat(published.ai().flagged()).isTrue();
    }

    private ReviewResponse createReview(UUID touristId, UUID siteId, String commentary) {
        return clientFor(touristId, "TOURIST")
                .post().uri("/api/v1/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "itemType":"SITE",
                          "itemId":"%s",
                          "rating":5,
                          "commentary":"%s",
                          "photosUrls":[]
                        }
                        """.formatted(siteId, commentary))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ReviewResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private WebTestClient clientFor(UUID userId, String role) {
        return webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestJwtSupport.bearer(jwtEncoder, userId, role, "Test User"))
                .build();
    }

    private static HttpServer startAiServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", AiReviewModerationIntegrationTests::respond);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start the littleLLM stub", exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String verdict = request.contains("reseña limpia")
                ? "{\"flagged\":false,\"score\":0.03,\"categories\":[],\"reason\":\"opinión legítima\"}"
                : "{\"flagged\":true,\"score\":0.91,\"categories\":[\"COORDINADA\"],\"reason\":\"texto repetido\"}";
        String content = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + verdict.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\"}}]}";
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
