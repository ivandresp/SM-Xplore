package com.smxplore.users.infrastructure.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.UserContactView;
import com.smxplore.users.application.port.in.GetUserContactUseCase;
import com.smxplore.users.application.port.out.InternalAccessLog;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.infrastructure.config.InternalApiProperties;
import com.smxplore.users.infrastructure.security.InternalRequestAuthenticator;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Pruebas del controlador interno sin levantar el contexto ni Docker (MS01-11).
 *
 * <p>Se verifica la firma HMAC por servicio, la identificación del emisor, el registro de auditoría de cada acceso y
 * el límite por servicio.
 */
class InternalContactControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String MASTER_KEY = "clave-maestra-de-prueba";
    private static final String SERVICE = "ms-03-reservations";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String PATH = "/internal/users/" + USER_ID + "/contact";
    private static final UserContactView CONTACT =
            new UserContactView(USER_ID, "ada@example.com", "Ada Turista", "TOURIST", "3001234567", true);

    private final RecordingAccessLog accessLog = new RecordingAccessLog();

    @Test
    void returnsTheContactForAValidSignatureAndAuditsTheAccess() {
        WebTestClient client = client(Mono.just(CONTACT), MASTER_KEY, List.of(), 60);

        client.get()
                .uri(PATH)
                .headers(headers -> sign(headers, SERVICE, NOW.getEpochSecond()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.userId")
                .isEqualTo(USER_ID.toString())
                .jsonPath("$.email")
                .isEqualTo("ada@example.com")
                .jsonPath("$.fullName")
                .isEqualTo("Ada Turista")
                .jsonPath("$.role")
                .isEqualTo("TOURIST")
                .jsonPath("$.phone")
                .isEqualTo("3001234567")
                .jsonPath("$.active")
                .isEqualTo(true);

        assertThat(accessLog.entries).singleElement().satisfies(entry -> {
            assertThat(entry.service()).isEqualTo(SERVICE);
            assertThat(entry.targetUserId()).isEqualTo(USER_ID);
            assertThat(entry.outcome()).isEqualTo("OK");
        });
    }

    @Test
    void returnsNotFoundAndAuditsWhenTheUserDoesNotExist() {
        WebTestClient client = client(Mono.error(UserNotFoundException::new), MASTER_KEY, List.of(), 60);

        client.get()
                .uri(PATH)
                .headers(headers -> sign(headers, SERVICE, NOW.getEpochSecond()))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("USER_NOT_FOUND"));

        assertThat(accessLog.entries)
                .singleElement()
                .satisfies(entry -> assertThat(entry.outcome()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void rejectsASignatureThatDoesNotMatch() {
        WebTestClient client = client(Mono.just(CONTACT), MASTER_KEY, List.of(), 60);

        client.get()
                .uri(PATH)
                .headers(headers -> {
                    headers.set(InternalContactController.SERVICE_HEADER, SERVICE);
                    headers.set(InternalContactController.TIMESTAMP_HEADER, String.valueOf(NOW.getEpochSecond()));
                    headers.set(InternalContactController.SIGNATURE_HEADER, "00".repeat(32));
                })
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("INVALID_INTERNAL_SIGNATURE"));

        assertThat(accessLog.entries)
                .singleElement()
                .satisfies(entry -> assertThat(entry.outcome()).isEqualTo("INVALID_SIGNATURE"));
    }

    @Test
    void rejectsARequestWithoutInternalHeaders() {
        WebTestClient client = client(Mono.just(CONTACT), MASTER_KEY, List.of(), 60);

        client.get()
                .uri(PATH)
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("INVALID_INTERNAL_SERVICE"));
    }

    @Test
    void rejectsAServiceThatIsNotAllowedEvenWithAValidSignature() {
        WebTestClient client = client(Mono.just(CONTACT), MASTER_KEY, List.of("ms-02-catalog"), 60);

        client.get()
                .uri(PATH)
                .headers(headers -> sign(headers, SERVICE, NOW.getEpochSecond()))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("INVALID_INTERNAL_SERVICE"));
    }

    @Test
    void rejectsATimestampOutsideTheClockSkew() {
        WebTestClient client = client(Mono.just(CONTACT), MASTER_KEY, List.of(), 60);

        client.get()
                .uri(PATH)
                .headers(headers ->
                        sign(headers, SERVICE, NOW.minus(Duration.ofMinutes(6)).getEpochSecond()))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("INVALID_INTERNAL_SIGNATURE"));
    }

    @Test
    void returnsServiceUnavailableWhenNoMasterKeyIsConfigured() {
        WebTestClient client = client(Mono.just(CONTACT), " ", List.of(), 60);

        client.get()
                .uri(PATH)
                .headers(headers -> sign(headers, SERVICE, NOW.getEpochSecond()))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("INTERNAL_API_NOT_CONFIGURED"));
    }

    @Test
    void limitsTheRequestsPerService() {
        WebTestClient client = client(Mono.just(CONTACT), MASTER_KEY, List.of(), 1);

        client.get()
                .uri(PATH)
                .headers(headers -> sign(headers, SERVICE, NOW.getEpochSecond()))
                .exchange()
                .expectStatus()
                .isOk();

        client.get()
                .uri(PATH)
                .headers(headers -> sign(headers, SERVICE, NOW.getEpochSecond()))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("RATE_LIMIT_EXCEEDED"));

        assertThat(accessLog.entries)
                .last()
                .satisfies(entry -> assertThat(entry.outcome()).isEqualTo("RATE_LIMITED"));
    }

    private WebTestClient client(
            Mono<UserContactView> result, String masterKey, List<String> allowedServices, int rateLimit) {
        GetUserContactUseCase useCase = userId -> result;
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InternalApiProperties properties =
                new InternalApiProperties(masterKey, allowedServices, Duration.ofMinutes(5), rateLimit);
        return WebTestClient.bindToController(new InternalContactController(
                        useCase, properties, new InternalRequestAuthenticator(properties, clock), accessLog, clock))
                .controllerAdvice(new com.smxplore.users.infrastructure.rest.ApiExceptionHandler())
                .build();
    }

    private static void sign(org.springframework.http.HttpHeaders headers, String service, long timestamp) {
        headers.set(InternalContactController.SERVICE_HEADER, service);
        headers.set(InternalContactController.TIMESTAMP_HEADER, String.valueOf(timestamp));
        String signingString = service + "\nGET\n" + PATH + "\n" + timestamp;
        headers.set(InternalContactController.SIGNATURE_HEADER, signature(service, signingString));
    }

    private static String signature(String service, String signingString) {
        byte[] serviceKey = hmac(MASTER_KEY.getBytes(StandardCharsets.UTF_8), service.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmac(serviceKey, signingString.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record LoggedAccess(String service, UUID targetUserId, String outcome) {}

    private static final class RecordingAccessLog implements InternalAccessLog {

        private final List<LoggedAccess> entries = new ArrayList<>();

        @Override
        public Mono<Void> record(String service, UUID targetUserId, String outcome, Instant occurredAt) {
            entries.add(new LoggedAccess(service, targetUserId, outcome));
            return Mono.empty();
        }
    }
}
