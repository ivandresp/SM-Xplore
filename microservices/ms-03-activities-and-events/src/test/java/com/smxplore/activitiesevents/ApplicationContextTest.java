package com.smxplore.activitiesevents;

import com.nimbusds.jose.jwk.JWKSet;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.indicator.DemandQuery;
import com.smxplore.activitiesevents.domain.model.indicator.DemandRecord;
import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.DemandQueryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReservationRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.ReviewRepositoryPort;
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
import java.time.Duration;
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

    @Autowired
    private ReviewRepositoryPort reviewRepository;

    @Autowired
    private DemandQueryPort demandQueryPort;

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
        registry.add("spring.mongodb.uri",
                () -> MONGO.getReplicaSetUrl() + "?uuidRepresentation=standard");
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
        var worker = UUID.randomUUID().toString();

        StepVerifier.create(outbox.enqueue(event)).verifyComplete();
        var claimed = outbox.claim(10, Duration.ofMinutes(1), 20, worker).collectList().block();
        assertThat(claimed).isNotNull();
        assertThat(claimed).extracting(claim -> claim.event().id()).contains(eventId);

        StepVerifier.create(outbox.markPublished(eventId, worker)).expectNext(true).verifyComplete();
        assertThat(outbox.claim(10, Duration.ofMinutes(1), 20, worker).collectList().block())
                .extracting(claim -> claim.event().id())
                .doesNotContain(eventId);
    }

    @Test
    void publicReviewQueriesOnlyCountPublishedDocuments() {
        var itemId = UUID.randomUUID();
        var now = Instant.now();
        var published = reviewRepository.save(Review.builder()
                .itemId(itemId)
                .itemType(ReviewItemType.ACTIVITY)
                .user(new ReviewUser(UUID.randomUUID(), "Publicada"))
                .rating(5)
                .status(ReviewStatus.PUBLISHED)
                .createdAt(now.minusSeconds(120))
                .updatedAt(now.minusSeconds(120))
                .build()).block();
        reviewRepository.save(Review.builder()
                .itemId(itemId)
                .itemType(ReviewItemType.ACTIVITY)
                .user(new ReviewUser(UUID.randomUUID(), "Pendiente"))
                .rating(4)
                .status(ReviewStatus.PENDING_REVIEW)
                .createdAt(now.minusSeconds(60))
                .updatedAt(now.minusSeconds(60))
                .build()).block();
        reviewRepository.save(Review.builder()
                .itemId(itemId)
                .itemType(ReviewItemType.ACTIVITY)
                .user(new ReviewUser(UUID.randomUUID(), "Oculta"))
                .rating(1)
                .status(ReviewStatus.HIDDEN)
                .createdAt(now)
                .updatedAt(now)
                .build()).block();

        var listed = reviewRepository.findByItem(itemId, ReviewItemType.ACTIVITY).collectList().block();
        assertThat(listed).isNotNull();
        assertThat(listed).extracting(Review::getId).containsExactly(published.getId());

        var metrics = reviewRepository.metrics(itemId, ReviewItemType.ACTIVITY).block();
        assertThat(metrics).isNotNull();
        assertThat(metrics.count()).isEqualTo(1);
        assertThat(metrics.averageRating()).isEqualTo(5.0);

        var pending = reviewRepository.findByStatus(ReviewStatus.PENDING_REVIEW, 10)
                .collectList().block();
        assertThat(pending).isNotNull();
        assertThat(pending).extracting(Review::getStatus).containsExactly(ReviewStatus.PENDING_REVIEW);
    }

    @Test
    void demandQueryJoinsReservationsWithActivitiesAndEvents() {
        var now = Instant.now();
        var start = now.plusSeconds(86_400);
        var activitySite = UUID.randomUUID();
        var eventSite = UUID.randomUUID();
        var activity = activityRepository.save(Activity.builder()
                .siteId(activitySite)
                .guideId(UUID.randomUUID())
                .name("Buceo de demanda")
                .category("Aventura")
                .status(ActivityStatus.PUBLISHED)
                .price(new java.math.BigDecimal("10000.00"))
                .maxCapacity(12)
                .durationMinutes(90)
                .startsAt(start)
                .endsAt(start.plusSeconds(5_400))
                .build()).block();
        var event = eventRepository.save(Event.builder()
                .siteId(eventSite)
                .siteName("Sitio del evento")
                .managerId(UUID.randomUUID())
                .name("Concierto de demanda")
                .category("Cultura")
                .status(EventStatus.PUBLISHED)
                .price(new java.math.BigDecimal("20000.00"))
                .maxCapacity(30)
                .expectedAttendance(25)
                .startsAt(start)
                .endsAt(start.plusSeconds(7_200))
                .build()).block();
        assertThat(activity).isNotNull();
        assertThat(event).isNotNull();

        reservationRepository.save(Reservation.builder()
                .touristId(UUID.randomUUID())
                .itemType(ReservationItemType.ACTIVITY)
                .itemId(activity.getId())
                .itemStartsAt(start)
                .unitPrice(new java.math.BigDecimal("10000.00"))
                .numberOfPeople(2)
                .total(new java.math.BigDecimal("20000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .status(ReservationStatus.CONFIRMED)
                .bookedFor(start)
                .build()).block();
        reservationRepository.save(Reservation.builder()
                .touristId(UUID.randomUUID())
                .itemType(ReservationItemType.EVENT)
                .itemId(event.getId())
                .itemStartsAt(start)
                .unitPrice(new java.math.BigDecimal("20000.00"))
                .numberOfPeople(3)
                .total(new java.math.BigDecimal("60000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .status(ReservationStatus.CONFIRMED)
                .bookedFor(start)
                .build()).block();

        var records = demandQueryPort.find(new DemandQuery(now.minusSeconds(3_600),
                        now.plusSeconds(172_800), null, List.of(activity.getId()),
                        List.of(event.getId())))
                .collectList().block();

        assertThat(records).isNotNull().hasSize(2);
        assertThat(records).extracting(DemandRecord::itemName)
                .containsExactlyInAnyOrder("Buceo de demanda", "Concierto de demanda");
        var activityRecord = records.stream()
                .filter(record -> record.itemType() == ReservationItemType.ACTIVITY)
                .findFirst().orElseThrow();
        assertThat(activityRecord.siteId()).isEqualTo(activitySite);
        assertThat(activityRecord.maxCapacity()).isEqualTo(12);
        assertThat(activityRecord.guideId()).isEqualTo(activity.getGuideId());
        var eventRecord = records.stream()
                .filter(record -> record.itemType() == ReservationItemType.EVENT)
                .findFirst().orElseThrow();
        assertThat(eventRecord.siteName()).isEqualTo("Sitio del evento");
        assertThat(eventRecord.managerId()).isEqualTo(event.getManagerId());
        assertThat(eventRecord.expectedAttendance()).isEqualTo(25);
        assertThat(eventRecord.maxCapacity()).isEqualTo(30);
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
        // booked_for must equal item_starts_at (reservations_booked_for_matches_start), so both
        // columns get the same value instead of two separate clock reads.
        var itemStart = Instant.now().plusSeconds(86_400);
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
                    .itemStartsAt(itemStart)
                    .bookedFor(itemStart)
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

    @Test
    void eventFiltersAreAppliedInTheDatabase() {
        var now = Instant.now();
        var sharedSite = UUID.randomUUID();
        eventRepository.save(Event.builder()
                .siteId(sharedSite)
                .name("Filtro Bahía Kayak")
                .category("Nautico")
                .status(EventStatus.PUBLISHED)
                .price(new java.math.BigDecimal("50000.00"))
                .maxCapacity(10)
                .startsAt(now.plusSeconds(86_400))
                .endsAt(now.plusSeconds(90_000))
                .build()).block();
        eventRepository.save(Event.builder()
                .siteId(sharedSite)
                .name("Filtro Bahía Concierto")
                .category("Musica")
                .status(EventStatus.PUBLISHED)
                .price(new java.math.BigDecimal("200000.00"))
                .maxCapacity(10)
                .startsAt(now.plusSeconds(86_400))
                .endsAt(now.plusSeconds(90_000))
                .build()).block();
        eventRepository.save(Event.builder()
                .siteId(sharedSite)
                .name("Filtro Bahía Borrador")
                .category("Nautico")
                .status(EventStatus.DRAFT)
                .price(new java.math.BigDecimal("50000.00"))
                .maxCapacity(10)
                .startsAt(now.plusSeconds(86_400))
                .endsAt(now.plusSeconds(90_000))
                .build()).block();

        var filter = new com.smxplore.activitiesevents.domain.model.event.EventFilter(
                "Filtro Bahia", EventStatus.PUBLISHED, "Nautico", sharedSite, null,
                new java.math.BigDecimal("10000.00"), new java.math.BigDecimal("100000.00"), null, null, 0, 20);
        var page = eventRepository.findPage(filter).block();

        assertThat(page).isNotNull();
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().getName()).isEqualTo("Filtro Bahía Kayak");
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void activityFiltersAreAppliedInTheDatabase() {
        var now = Instant.now();
        var start = now.plusSeconds(86_400);
        var sharedSite = UUID.randomUUID();
        activityRepository.save(Activity.builder()
                .siteId(sharedSite)
                .guideId(UUID.randomUUID())
                .name("Filtro Bahia Buceo")
                .category("Aventura")
                .status(ActivityStatus.PUBLISHED)
                .price(new java.math.BigDecimal("50000.00"))
                .maxCapacity(10)
                .durationMinutes(90)
                .startsAt(start)
                .endsAt(start.plusSeconds(5_400))
                .build()).block();
        activityRepository.save(Activity.builder()
                .siteId(sharedSite)
                .guideId(UUID.randomUUID())
                .name("Filtro Bahia Buceo")
                .category("Aventura")
                .status(ActivityStatus.DRAFT)
                .price(new java.math.BigDecimal("50000.00"))
                .maxCapacity(10)
                .durationMinutes(90)
                .startsAt(start)
                .endsAt(start.plusSeconds(5_400))
                .build()).block();

        var filter = new com.smxplore.activitiesevents.domain.model.activity.ActivityFilter(
                "Filtro Bahia", ActivityStatus.PUBLISHED, "Aventura", sharedSite, null,
                new java.math.BigDecimal("10000.00"), new java.math.BigDecimal("100000.00"),
                start.minusSeconds(3_600), start.plusSeconds(3_600), 0, 20);
        var page = activityRepository.findPage(filter).block();

        assertThat(page).isNotNull();
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().getStatus()).isEqualTo(ActivityStatus.PUBLISHED);
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void publishedEventPagesAreLimitedInTheDatabase() {        var now = Instant.now();
        for (int index = 0; index < 3; index++) {
            eventRepository.save(Event.builder()
                    .siteId(UUID.randomUUID())
                    .managerId(UUID.randomUUID())
                    .name("Evento de pagina " + index)
                    .category("Cultura")
                    .status(EventStatus.PUBLISHED)
                    .price(new java.math.BigDecimal("10000.00"))
                    .maxCapacity(10)
                    .startsAt(now.plusSeconds(86_400 + index))
                    .endsAt(now.plusSeconds(90_000 + index))
                    .build()).block();
        }

        var first = eventRepository.findPage(com.smxplore.activitiesevents.domain.model.event.EventFilter.published(0, 2)).block();
        var second = eventRepository.findPage(com.smxplore.activitiesevents.domain.model.event.EventFilter.published(1, 2)).block();

        assertThat(first).isNotNull();
        assertThat(first.content()).hasSize(2);
        assertThat(first.total()).isGreaterThanOrEqualTo(3);

        assertThat(second).isNotNull();
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.content()).isNotEmpty();
    }

    @Test
    void appliesTheSiteSnapshotOnlyWhenTheSiteEventIsNewer() {
        var siteId = UUID.randomUUID();
        var formerManager = UUID.randomUUID();
        var newManager = UUID.randomUUID();
        var itemStart = Instant.now().plusSeconds(86_400);

        var saved = eventRepository.save(Event.builder()
                .siteId(siteId)
                .managerId(formerManager)
                .siteName("Nombre viejo")
                .name("Evento con snapshot")
                .category("Cultura")
                .status(EventStatus.PUBLISHED)
                .price(new java.math.BigDecimal("10000.00"))
                .maxCapacity(10)
                .startsAt(itemStart)
                .endsAt(itemStart.plusSeconds(3_600))
                .build()).block();
        assertThat(saved).isNotNull();

        var newer = Instant.now();
        assertThat(eventRepository.applySiteSnapshot(siteId, "Nombre nuevo", newManager, newer).block())
                .isEqualTo(1);

        var refreshed = eventRepository.findById(saved.getId()).block();
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.getManagerId()).isEqualTo(newManager);
        assertThat(refreshed.getSiteName()).isEqualTo("Nombre nuevo");

        // An older site event must not hand the site back to the previous manager.
        assertThat(eventRepository.applySiteSnapshot(siteId, "Nombre viejo", formerManager,
                newer.minusSeconds(60)).block()).isZero();

        var stillNew = eventRepository.findById(saved.getId()).block();
        assertThat(stillNew).isNotNull();
        assertThat(stillNew.getManagerId()).isEqualTo(newManager);
        assertThat(stillNew.getSiteName()).isEqualTo("Nombre nuevo");
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
