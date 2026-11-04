package com.smxplore.users.infrastructure.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.ReviewAiRequest;
import com.smxplore.users.domain.model.ReviewAiAction;
import com.smxplore.users.domain.model.ReviewAiAssessment;
import com.smxplore.users.infrastructure.config.ReviewAiProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LittleLlmReviewAiFilterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final ReviewAiRequest REQUEST =
            new ReviewAiRequest("PRESTADORA_SERVICIOS", 5, "Excelente servicio, muy recomendado");

    @Test
    void parsesFlaggedResponseAndSendsTheConfiguredApiKey() {
        try (StubLlm llm = StubLlm.start((exchange, body) -> reply(
                exchange,
                200,
                chatResponse("{\"flagged\":true,\"score\":0.88,\"categories\":[\"SPAM\"],\"reason\":\"spam\"}")))) {
            ReviewAiAssessment assessment =
                    filter(llm.baseUrl(), Duration.ofSeconds(2)).assess(REQUEST).block();

            assertThat(assessment).isNotNull();
            assertThat(assessment.status()).isEqualTo(ReviewAiAssessment.Status.ASSESSED);
            assertThat(assessment.flagged()).isTrue();
            assertThat(assessment.score()).isEqualTo(0.88);
            assertThat(assessment.categories()).containsExactly("SPAM");
            assertThat(assessment.model()).isEqualTo("littlellm");
            assertThat(llm.authorization.get()).isEqualTo("Bearer secret-key");
        }
    }

    @Test
    void parsesCleanResponse() {
        try (StubLlm llm = StubLlm.start((exchange, body) ->
                reply(exchange, 200, chatResponse("{\"flagged\":false,\"score\":0.04,\"categories\":[]}")))) {
            ReviewAiAssessment assessment =
                    filter(llm.baseUrl(), Duration.ofSeconds(2)).assess(REQUEST).block();

            assertThat(assessment.status()).isEqualTo(ReviewAiAssessment.Status.ASSESSED);
            assertThat(assessment.flagged()).isFalse();
        }
    }

    @Test
    void returnsUnavailableWhenTheServerFails() {
        try (StubLlm llm = StubLlm.start((exchange, body) -> reply(exchange, 500, "{}"))) {
            ReviewAiAssessment assessment =
                    filter(llm.baseUrl(), Duration.ofSeconds(2)).assess(REQUEST).block();

            assertThat(assessment.status()).isEqualTo(ReviewAiAssessment.Status.UNAVAILABLE);
            assertThat(assessment.isUnavailable()).isTrue();
        }
    }

    @Test
    void returnsUnavailableWhenTheModelDoesNotReturnJson() {
        try (StubLlm llm = StubLlm.start((exchange, body) -> reply(exchange, 200, chatResponse("no json")))) {
            ReviewAiAssessment assessment =
                    filter(llm.baseUrl(), Duration.ofSeconds(2)).assess(REQUEST).block();

            assertThat(assessment.status()).isEqualTo(ReviewAiAssessment.Status.UNAVAILABLE);
        }
    }

    @Test
    void returnsUnavailableWhenTheServerIsTooSlow() {
        try (StubLlm llm = StubLlm.start((exchange, body) -> {
            sleep(500);
            reply(exchange, 200, chatResponse("{\"flagged\":true,\"score\":0.9}"));
        })) {
            ReviewAiAssessment assessment = filter(llm.baseUrl(), Duration.ofMillis(100))
                    .assess(REQUEST)
                    .block();

            assertThat(assessment.status()).isEqualTo(ReviewAiAssessment.Status.UNAVAILABLE);
        }
    }

    @Test
    void sendsTheConfiguredTemperature() {
        try (StubLlm llm = StubLlm.start(
                (exchange, body) -> reply(exchange, 200, chatResponse("{\"flagged\":false,\"score\":0.05}")))) {
            filter(llm.baseUrl(), Duration.ofSeconds(2), 0.4).assess(REQUEST).block();

            assertThat(llm.lastBody.get()).contains("\"temperature\":0.4");
        }
    }

    @Test
    void skipsWithoutCallingTheServerWhenDisabled() {
        ReviewAiProperties properties = new ReviewAiProperties(
                false,
                null,
                "",
                "Authorization",
                "Bearer",
                "littlellm",
                "/v1/chat/completions",
                Duration.ofSeconds(2),
                0.7,
                0.0,
                ReviewAiAction.PENDING_REVIEW,
                true,
                false);
        LittleLlmReviewAiFilter filter = new LittleLlmReviewAiFilter(properties, CLOCK);

        ReviewAiAssessment assessment = filter.assess(REQUEST).block();

        assertThat(assessment.status()).isEqualTo(ReviewAiAssessment.Status.SKIPPED);
    }

    private static LittleLlmReviewAiFilter filter(String baseUrl, Duration timeout) {
        return filter(baseUrl, timeout, 0.0);
    }

    private static LittleLlmReviewAiFilter filter(String baseUrl, Duration timeout, double temperature) {
        ReviewAiProperties properties = new ReviewAiProperties(
                true,
                baseUrl,
                "secret-key",
                "Authorization",
                "Bearer",
                "littlellm",
                "/v1/chat/completions",
                timeout,
                0.7,
                temperature,
                ReviewAiAction.PENDING_REVIEW,
                true,
                false);
        return new LittleLlmReviewAiFilter(properties, CLOCK);
    }

    private static String chatResponse(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + escape(content) + "\"}}]}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class StubLlm implements AutoCloseable {

        @FunctionalInterface
        private interface Responder {
            void respond(HttpExchange exchange, String requestBody) throws IOException;
        }

        private final HttpServer server;
        private final AtomicReference<String> authorization = new AtomicReference<>();
        private final AtomicReference<String> lastBody = new AtomicReference<>();

        private StubLlm(HttpServer server, Responder responder) {
            this.server = server;
            this.server.createContext("/v1/chat/completions", exchange -> {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                lastBody.set(body);
                try {
                    responder.respond(exchange, body);
                } finally {
                    exchange.close();
                }
            });
            this.server.start();
        }

        static StubLlm start(Responder responder) {
            try {
                return new StubLlm(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0), responder);
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
