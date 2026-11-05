package com.smxplore.activitiesevents;

import com.nimbusds.jose.jwk.JWKSet;
import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationContextTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static final KeyPair KEYS = generateKeys();
    private static final Path PUBLIC_KEY = writePublicKey(KEYS);

    @LocalServerPort
    private int port;

    @Autowired
    private Flyway flyway;

    @Autowired
    private ReactiveJwtDecoder jwtDecoder;

    @Autowired
    private ReservationRepositoryPort reservationRepository;

    @Autowired
    private EventRepositoryPort eventRepository;

    @Autowired
    private ActivityRepositoryPort activityRepository;

    @Autowired
    private OutboxPort outbox;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        var jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName());
        var r2dbcUrl = "r2dbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.url", () -> r2dbcUrl);
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "false");
        registry.add("app.security.jwt.public-key-location", () -> PUBLIC_KEY.toUri().toString());
        registry.add("app.security.jwt.issuer", () -> "sm-xplore-ms-01");
        registry.add("app.security.jwt.audience", () -> "sm-xplore-services");
    }

    @Test
    void contextLoadsAndFlywayMigratesTheSchema() {
        assertThat(flyway.info().current().getVersion())
                .isGreaterThanOrEqualTo(MigrationVersion.fromVersion("2"));
    }

    @Test
    void decoderAcceptsAnAdminToken() {
        var decoded = jwtDecoder.decode(token("ADMIN")).block();

        assertThat(decoded).isNotNull();
        assertThat(decoded.getClaimAsString("role")).isEqualTo("ADMIN");
    }

    @Test
    void securityChainProtectsTheGlobalReservationListing() {
        var client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        client.get().uri("/api/v1/reservations?status=CONFIRMED")
                .exchange()
                .expectStatus().isUnauthorized();

        client.get().uri("/api/v1/reservations?status=CONFIRMED")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("TOURIST"))
                .exchange()
                .expectStatus().isForbidden();

        client.get().uri("/api/v1/reservations?status=CONFIRMED")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void outboxStoresAndPublishesAnEvent() {
        var eventId = UUID.randomUUID();
        var event = new OutboxEvent(eventId, "bookings.reservation.changed", 1,
                UUID.randomUUID(), UUID.randomUUID(), "{\"test\":true}", java.time.Instant.now());

        StepVerifier.create(outbox.enqueue(event)).verifyComplete();
        assertThat(outbox.findPending(10).collectList().block())
                .extracting(OutboxEvent::id)
                .contains(eventId);

        StepVerifier.create(outbox.markPublished(eventId)).verifyComplete();
        assertThat(outbox.findPending(10).collectList().block())
                .extracting(OutboxEvent::id)
                .doesNotContain(eventId);
    }

    @Test
    void managerQueriesSupportCollectionParameters() {
        var itemId = UUID.randomUUID();

        StepVerifier.create(eventRepository.findIdsBySiteIds(Set.of(itemId)))
                .verifyComplete();
        StepVerifier.create(activityRepository.findIdsBySiteIds(Set.of(itemId)))
                .verifyComplete();
        StepVerifier.create(reservationRepository.findPageByItemRefs(
                        Set.of(itemId), Set.of(itemId), ReservationStatus.CONFIRMED, 0, 10))
                .assertNext(page -> {
                    assertThat(page.content()).isEmpty();
                    assertThat(page.total()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void reservationPagesApplyTheOffsetInTheDatabaseAndReturnTheTotal() {
        for (int index = 0; index < 3; index++) {
            reservationRepository.save(Reservation.builder()
                    .id(UUID.randomUUID())
                    .touristId(UUID.randomUUID())
                    .itemType(ReservationItemType.ACTIVITY)
                    .itemId(UUID.randomUUID())
                    .numberOfPeople(1)
                    .unitPrice(java.math.BigDecimal.TEN)
                    .total(java.math.BigDecimal.TEN)
                    .paymentMethod(PaymentMethod.CASH)
                    .status(ReservationStatus.CONFIRMED)
                    .itemStartsAt(Instant.now().plusSeconds(86_400))
                    .bookedFor(Instant.now().plusSeconds(86_400))
                    .build()).block();
        }

        var first = reservationRepository
                .findPageByStatus(ReservationStatus.CONFIRMED, 0, 2).block();
        var second = reservationRepository
                .findPageByStatus(ReservationStatus.CONFIRMED, 1, 2).block();

        assertThat(first).isNotNull();
        assertThat(first.content()).hasSize(2);
        // The total is computed by the database over the whole status, not over the page.
        assertThat(first.total()).isGreaterThanOrEqualTo(3);
        assertThat(first.totalPages()).isGreaterThanOrEqualTo(2);

        assertThat(second).isNotNull();
        assertThat(second.content()).isNotEmpty();
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.content())
                .isNotEmpty()
                .allSatisfy(reservation -> assertThat(first.content()).doesNotContain(reservation));
    }

    private static String token(String role) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer("sm-xplore-ms-01")
                .audience(List.of("sm-xplore-services"))
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", role)
                .build();
        var rsaKey = new RSAKey.Builder((RSAPublicKey) KEYS.getPublic())
                .privateKey((RSAPrivateKey) KEYS.getPrivate())
                .build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    private static KeyPair generateKeys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Path writePublicKey(KeyPair keyPair) {
        try {
            var encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(keyPair.getPublic().getEncoded());
            var path = Files.createTempFile("ms-03-jwt-public-", ".pem");
            Files.writeString(path, "-----BEGIN PUBLIC KEY-----\n" + encoded
                    + "\n-----END PUBLIC KEY-----\n");
            return path;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
