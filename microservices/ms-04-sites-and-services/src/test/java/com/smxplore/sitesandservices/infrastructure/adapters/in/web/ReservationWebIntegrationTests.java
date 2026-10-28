package com.smxplore.sitesandservices.infrastructure.adapters.in.web;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.TestJwtSupport;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category.CategoryResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.ReservationAvailabilityResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.ReservationResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service.ServiceResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site.SiteResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone.ZoneResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
class ReservationWebIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    private static final ZoneId BOOKING_ZONE = ZoneId.of("America/Bogota");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void createsReadsAndCancelsConfirmedReservations() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        ServiceResponse service = createService(managerId, providerId, 20, "PUBLISHED");
        Instant bookedFor = nextBookingInstant();

        ReservationResponse reservation = createReservation(
                touristId, bookedFor, itemBody(service.serviceId(), 2));

        assertThat(reservation.reservationId()).isNotNull();
        assertThat(reservation.touristId()).isEqualTo(touristId);
        assertThat(reservation.status().name()).isEqualTo("CONFIRMED");
        assertThat(reservation.confirmedAt()).isNotNull();
        assertThat(reservation.total()).isEqualByComparingTo("100000.00");
        assertThat(reservation.numberOfPeople()).isEqualTo(2);
        assertThat(reservation.items()).hasSize(1);
        assertThat(reservation.items().getFirst().serviceId()).isEqualTo(service.serviceId());
        assertThat(reservation.items().getFirst().unitPrice()).isEqualByComparingTo("50000.00");

        getReservation(touristId, "TURISTA", reservation.reservationId(), 200);
        getReservation(providerId, "PRESTADORA_SERVICIOS", reservation.reservationId(), 200);
        getReservation(UUID.randomUUID(), "TURISTA", reservation.reservationId(), 403);

        PageResponse<ReservationResponse> touristReservations = listReservations(
                touristId, "TURISTA", "");
        assertThat(touristReservations.total()).isEqualTo(1);

        PageResponse<ReservationResponse> providerReservations = listReservations(
                providerId, "PRESTADORA_SERVICIOS", "");
        assertThat(providerReservations.total()).isEqualTo(1);

        ReservationAvailabilityResponse availability = availability(
                touristId, service.serviceId(), bookedFor, 1);
        assertThat(availability.reservedPeople()).isEqualTo(2);
        assertThat(availability.remainingCapacity()).isEqualTo(18);
        assertThat(availability.available()).isTrue();

        clientFor(providerId, "PRESTADORA_SERVICIOS")
                .patch().uri("/api/v1/reservations/" + reservation.reservationId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"status":"CONFIRMED"}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").value(detail ->
                        assertThat(detail.toString()).contains("confirmed automatically"));

        ReservationResponse cancelledByProvider = changeStatus(
                providerId,
                "PRESTADORA_SERVICIOS",
                reservation.reservationId(),
                "CANCELLED",
                null
        );
        assertThat(cancelledByProvider.status().name()).isEqualTo("CANCELLED");
        assertThat(cancelledByProvider.cancelledAt()).isNotNull();

        ReservationResponse second = createReservation(
                touristId, bookedFor, itemBody(service.serviceId(), 1));
        ReservationResponse cancelledByTourist = changeStatus(
                touristId,
                "TURISTA",
                second.reservationId(),
                "CANCELLED",
                "Cambio de planes"
        );
        assertThat(cancelledByTourist.status().name()).isEqualTo("CANCELLED");
        assertThat(cancelledByTourist.cancellationReason()).isEqualTo("Cambio de planes");

        ReservationAvailabilityResponse released = availability(
                touristId, service.serviceId(), bookedFor, 1);
        assertThat(released.reservedPeople()).isZero();
        assertThat(released.remainingCapacity()).isEqualTo(20);

        List<String> events = outboxEvents(reservation.reservationId());
        assertThat(events).hasSize(2);
        assertThat(events).anyMatch(event -> event.contains("\"status\":\"CONFIRMED\""));
        assertThat(events).anyMatch(event -> event.contains("\"status\":\"CANCELLED\""));
        assertThat(events).allMatch(event -> event.contains("\"eventType\":\"bookings.reservation.changed\""));
    }

    @Test
    void listsReservationsWithTheirItemsInOnePage() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        ServiceResponse service = createService(managerId, providerId, 10, "PUBLISHED");
        Instant bookedFor = nextBookingInstant();

        ReservationResponse first = createReservation(
                touristId, bookedFor, itemBody(service.serviceId(), 2));
        ReservationResponse second = createReservation(
                touristId, bookedFor, itemBody(service.serviceId(), 1));

        PageResponse<ReservationResponse> page = listReservations(
                touristId, "TURISTA", "?size=10");

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.content())
                .extracting(ReservationResponse::reservationId)
                .containsExactlyInAnyOrder(first.reservationId(), second.reservationId());
        ReservationResponse firstFromPage = page.content().stream()
                .filter(item -> item.reservationId().equals(first.reservationId()))
                .findFirst()
                .orElseThrow();
        ReservationResponse secondFromPage = page.content().stream()
                .filter(item -> item.reservationId().equals(second.reservationId()))
                .findFirst()
                .orElseThrow();
        assertThat(firstFromPage.items()).hasSize(1);
        assertThat(firstFromPage.total()).isEqualByComparingTo("100000.00");
        assertThat(secondFromPage.items()).hasSize(1);
        assertThat(secondFromPage.total()).isEqualByComparingTo("50000.00");
    }

    @Test
    void enforcesTheActiveReservationLimitPerTourist() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        ServiceResponse service = createService(managerId, providerId, 100, "PUBLISHED");
        Instant bookedFor = nextBookingInstant();

        List<ReservationResponse> active = IntStream.range(0, 5)
                .mapToObj(index -> createReservation(
                        touristId, bookedFor, itemBody(service.serviceId(), 1)))
                .toList();
        assertThat(active).hasSize(5);

        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(bookedFor, itemBody(service.serviceId(), 1)))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Reservation limit reached");

        changeStatus(
                touristId, "TURISTA", active.getFirst().reservationId(), "CANCELLED", null);

        ReservationResponse replacement = createReservation(
                touristId, bookedFor, itemBody(service.serviceId(), 1));
        assertThat(replacement.status().name()).isEqualTo("CONFIRMED");
    }

    @Test
    void capacityIsEnforcedAndReleasedWhenCancelling() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        ServiceResponse service = createService(managerId, providerId, 3, "PUBLISHED");
        Instant bookedFor = nextBookingInstant();

        ReservationResponse first = createReservation(
                touristId, bookedFor, itemBody(service.serviceId(), 2));

        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(bookedFor, itemBody(service.serviceId(), 2)))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Reservation conflict");

        ReservationAvailabilityResponse full = availability(
                touristId, service.serviceId(), bookedFor, 1);
        assertThat(full.reservedPeople()).isEqualTo(2);
        assertThat(full.remainingCapacity()).isEqualTo(1);
        assertThat(full.available()).isTrue();

        changeStatus(touristId, "TURISTA", first.reservationId(), "CANCELLED", null);

        ReservationResponse replacement = createReservation(
                touristId, bookedFor, itemBody(service.serviceId(), 2));
        assertThat(replacement.status().name()).isEqualTo("CONFIRMED");

        ReservationAvailabilityResponse after = availability(
                touristId, service.serviceId(), bookedFor, 1);
        assertThat(after.reservedPeople()).isEqualTo(2);
    }

    @Test
    void createsOneReservationForSeveralServicesOfTheSameProvider() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        ServiceResponse restaurant = createService(managerId, providerId, 10, "PUBLISHED");
        ServiceResponse transport = createService(managerId, providerId, 10, "PUBLISHED");
        Instant bookedFor = nextBookingInstant();

        ReservationResponse reservation = createReservation(
                touristId,
                bookedFor,
                itemBody(restaurant.serviceId(), 2) + "," + itemBody(transport.serviceId(), 1)
        );

        assertThat(reservation.status().name()).isEqualTo("CONFIRMED");
        assertThat(reservation.items()).hasSize(2);
        assertThat(reservation.numberOfPeople()).isEqualTo(3);
        assertThat(reservation.total()).isEqualByComparingTo("150000.00");
        assertThat(reservation.items())
                .extracting(item -> item.position())
                .containsExactly(0, 1);
        assertThat(reservation.items())
                .allMatch(item -> item.providerId().equals(providerId));
    }

    @Test
    void rejectsItemsFromDifferentProviders() {
        UUID managerId = UUID.randomUUID();
        UUID firstProviderId = UUID.randomUUID();
        UUID secondProviderId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        ServiceResponse firstService = createService(managerId, firstProviderId, 10, "PUBLISHED");
        ServiceResponse secondService = createService(managerId, secondProviderId, 10, "PUBLISHED");
        Instant bookedFor = nextBookingInstant();

        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(
                        bookedFor,
                        itemBody(firstService.serviceId(), 1)
                                + "," + itemBody(secondService.serviceId(), 1)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").value(detail ->
                        assertThat(detail.toString()).contains("single provider"));
    }

    @Test
    void rejectsServicesThatAreNotPublishedOrOutsideTheirSchedule() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        ServiceResponse draft = createService(managerId, providerId, 10, "DRAFT");
        Instant bookedFor = nextBookingInstant();

        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(bookedFor, itemBody(draft.serviceId(), 1)))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").value(detail ->
                        assertThat(detail.toString()).contains("SERVICE_NOT_PUBLISHED"));

        ServiceResponse published = createService(managerId, providerId, 10, "PUBLISHED");
        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(
                        wrongDayBookingInstant(),
                        itemBody(published.serviceId(), 1)))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").value(detail ->
                        assertThat(detail.toString()).contains("OUTSIDE_SERVICE_AVAILABILITY"));
    }

    @Test
    void availabilityRejectsPastDatesAndMissingIdempotencyKey() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        ServiceResponse service = createService(managerId, providerId, 10, "PUBLISHED");

        ReservationAvailabilityResponse past = availability(
                touristId,
                service.serviceId(),
                Instant.now().minusSeconds(3600),
                1
        );
        assertThat(past.available()).isFalse();
        assertThat(past.reason()).isEqualTo("BOOKED_FOR_IN_PAST");

        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(
                        nextBookingInstant(),
                        itemBody(service.serviceId(), 1)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.detail").value(detail ->
                        assertThat(detail.toString()).contains("Idempotency-Key"));
    }

    @Test
    void reusesTheSameReservationForTheSameIdempotencyKey() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        ServiceResponse service = createService(managerId, providerId, 10, "PUBLISHED");
        Instant bookedFor = nextBookingInstant();
        String idempotencyKey = UUID.randomUUID().toString();
        String items = itemBody(service.serviceId(), 2);

        ReservationResponse first = createReservation(
                touristId, bookedFor, items, idempotencyKey);
        ReservationResponse replay = createReservation(
                touristId, bookedFor, items, idempotencyKey);

        assertThat(replay.reservationId()).isEqualTo(first.reservationId());
        assertThat(outboxEvents(first.reservationId())).hasSize(1);

        ReservationAvailabilityResponse availability = availability(
                touristId, service.serviceId(), bookedFor, 1);
        assertThat(availability.reservedPeople()).isEqualTo(2);

        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(bookedFor, itemBody(service.serviceId(), 1)))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").value(detail ->
                        assertThat(detail.toString()).contains("different reservation payload"));
    }

    @Test
    void rejectsMoreThanTenItemsEvenIfTheyAreDifferentServices() {
        UUID touristId = UUID.randomUUID();
        String items = IntStream.range(0, 11)
                .mapToObj(index -> itemBody(UUID.randomUUID(), 1))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();

        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(nextBookingInstant(), items))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void replaysIdempotentReservationEvenWhenServiceIsNoLongerAvailable() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        ServiceResponse service = createService(managerId, providerId, 10, "PUBLISHED");
        Instant bookedFor = nextBookingInstant();
        String idempotencyKey = UUID.randomUUID().toString();
        String items = itemBody(service.serviceId(), 2);

        ReservationResponse first = createReservation(
                touristId, bookedFor, items, idempotencyKey);

        clientFor(providerId, "PRESTADORA_SERVICIOS")
                .patch().uri("/api/v1/services/" + service.serviceId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"status":"INACTIVE"}
                        """)
                .exchange()
                .expectStatus().isOk();

        ReservationResponse replay = createReservation(
                touristId, bookedFor, items, idempotencyKey);

        assertThat(replay.reservationId()).isEqualTo(first.reservationId());
        assertThat(replay.status().name()).isEqualTo("CONFIRMED");
    }

    @Test
    void rejectsDuplicateItemsPastDatesAndInvalidRoleChanges() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        ServiceResponse service = createService(managerId, providerId, 10, "PUBLISHED");
        Instant bookedFor = nextBookingInstant();

        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(
                        bookedFor,
                        itemBody(service.serviceId(), 1) + "," + itemBody(service.serviceId(), 1)))
                .exchange()
                .expectStatus().isBadRequest();

        clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(
                        Instant.now().minusSeconds(3600),
                        itemBody(service.serviceId(), 1)))
                .exchange()
                .expectStatus().isBadRequest();

        ReservationResponse reservation = createReservation(
                touristId, bookedFor, itemBody(service.serviceId(), 1));

        clientFor(touristId, "TURISTA")
                .patch().uri("/api/v1/reservations/" + reservation.reservationId() + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"status":"COMPLETED"}
                        """)
                .exchange()
                .expectStatus().isForbidden();

        clientFor(providerId, "PRESTADORA_SERVICIOS")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(bookedFor, itemBody(service.serviceId(), 1)))
                .exchange()
                .expectStatus().isForbidden();

        clientFor(UUID.randomUUID(), "PRESTADORA_SERVICIOS")
                .get().uri("/api/v1/reservations/" + reservation.reservationId())
                .exchange()
                .expectStatus().isForbidden();

        clientFor(managerId, "GESTORA_DESTINOS")
                .get().uri("/api/v1/reservations")
                .exchange()
                .expectStatus().isForbidden();
    }

    private ReservationResponse createReservation(
            UUID touristId,
            Instant bookedFor,
            String items
    ) {
        return createReservation(touristId, bookedFor, items, UUID.randomUUID().toString());
    }

    private ReservationResponse createReservation(
            UUID touristId,
            Instant bookedFor,
            String items,
            String idempotencyKey
    ) {
        return clientFor(touristId, "TURISTA")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(reservationBody(bookedFor, items))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ReservationResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private String reservationBody(Instant bookedFor, String items) {
        return """
                {
                  "bookedFor":"%s",
                  "notes":"Reserva simbólica de prueba",
                  "items":[%s]
                }
                """.formatted(bookedFor, items);
    }

    private String itemBody(UUID serviceId, int people) {
        return """
                {"serviceId":"%s","people":%d}
                """.formatted(serviceId, people);
    }

    private void getReservation(
            UUID userId,
            String role,
            UUID reservationId,
            int expectedStatus
    ) {
        clientFor(userId, role)
                .get().uri("/api/v1/reservations/" + reservationId)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
    }

    private PageResponse<ReservationResponse> listReservations(
            UUID userId,
            String role,
            String query
    ) {
        return clientFor(userId, role)
                .get().uri("/api/v1/reservations" + query)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<PageResponse<ReservationResponse>>() {
                })
                .returnResult()
                .getResponseBody();
    }

    private ReservationAvailabilityResponse availability(
            UUID userId,
            UUID serviceId,
            Instant bookedFor,
            int people
    ) {
        return clientFor(userId, "TURISTA")
                .get().uri(uriBuilder -> uriBuilder
                        .path("/api/v1/reservations/availability")
                        .queryParam("serviceId", serviceId)
                        .queryParam("bookedFor", bookedFor)
                        .queryParam("people", people)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReservationAvailabilityResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ReservationResponse changeStatus(
            UUID userId,
            String role,
            UUID reservationId,
            String status,
            String reason
    ) {
        String body = reason == null
                ? """
                  {"status":"%s"}
                  """.formatted(status)
                : """
                  {"status":"%s","reason":"%s"}
                  """.formatted(status, reason);
        return clientFor(userId, role)
                .patch().uri("/api/v1/reservations/" + reservationId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ReservationResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private List<String> outboxEvents(UUID reservationId) {
        return databaseClient.sql("""
                        SELECT event_json
                        FROM reservation_outbox
                        WHERE aggregate_id = :aggregateId
                        ORDER BY occurred_at ASC
                        """)
                .bind("aggregateId", reservationId)
                .map((row, metadata) -> row.get("event_json", String.class))
                .all()
                .collectList()
                .block();
    }

    private ServiceResponse createService(
            UUID managerId,
            UUID providerId,
            int maxCapacity,
            String status
    ) {
        CategoryResponse category = createCategory(managerId);
        ZoneResponse zone = createZone(managerId);
        SiteResponse site = createSite(managerId, category.categoryId(), zone.zoneId());
        return clientFor(providerId, "PRESTADORA_SERVICIOS")
                .post().uri("/api/v1/services")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name":"Servicio %s",
                          "description":"Servicio de prueba",
                          "type":"RESTAURANT",
                          "categoryId":"%s",
                          "siteId":"%s",
                          "zoneId":"%s",
                          "latitude":10.39,
                          "longitude":-75.47,
                          "price":50000,
                          "maxCapacity":%d,
                          "availability":[{"dayOfWeek":"MONDAY","opensAt":"10:00:00","closesAt":"18:00:00"}],
                          "photosUrls":[],
                          "cancellationPolicy":"48 horas",
                          "status":"%s"
                        }
                        """.formatted(
                        UUID.randomUUID(),
                        category.categoryId(),
                        site.siteId(),
                        zone.zoneId(),
                        maxCapacity,
                        status
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ServiceResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private CategoryResponse createCategory(UUID managerId) {
        return clientFor(managerId, "GESTORA_DESTINOS")
                .post().uri("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Naturaleza","scope":"GENERAL","parentId":null}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ZoneResponse createZone(UUID managerId) {
        return clientFor(managerId, "GESTORA_DESTINOS")
                .post().uri("/api/v1/zones")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"Centro","type":"URBAN","description":"Zona céntrica","status":"ACTIVE"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ZoneResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private SiteResponse createSite(UUID managerId, UUID categoryId, UUID zoneId) {
        return clientFor(managerId, "GESTORA_DESTINOS")
                .post().uri("/api/v1/sites")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name":"Playa de prueba %s",
                          "description":"Un sitio de prueba",
                          "categoryId":"%s",
                          "zoneId":"%s",
                          "latitude":10.39,
                          "longitude":-75.47,
                          "photosUrls":[],
                          "maxCapacity":100,
                          "availability":[{"dayOfWeek":"MONDAY","opensAt":"09:00:00","closesAt":"17:00:00"}],
                          "status":"PUBLISHED"
                        }
                        """.formatted(UUID.randomUUID(), categoryId, zoneId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(SiteResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private Instant nextBookingInstant() {
        return nextWeekdayInstant(DayOfWeek.MONDAY, LocalTime.NOON);
    }

    private Instant wrongDayBookingInstant() {
        return nextWeekdayInstant(DayOfWeek.TUESDAY, LocalTime.NOON);
    }

    private Instant nextWeekdayInstant(DayOfWeek dayOfWeek, LocalTime time) {
        LocalDate date = LocalDate.now(BOOKING_ZONE).plusWeeks(2);
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.plusDays(1);
        }
        return ZonedDateTime.of(date, time, BOOKING_ZONE).toInstant();
    }

    private WebTestClient clientFor(UUID userId, String role) {
        return webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestJwtSupport.bearer(jwtEncoder, userId, role, "Test User"))
                .build();
    }
}
