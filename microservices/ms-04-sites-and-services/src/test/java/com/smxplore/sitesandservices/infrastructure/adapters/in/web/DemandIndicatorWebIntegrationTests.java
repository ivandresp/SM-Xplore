package com.smxplore.sitesandservices.infrastructure.adapters.in.web;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.TestJwtSupport;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category.CategoryResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.indicator.DemandIndicatorsResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.ReservationResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service.ServiceResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site.SiteResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone.ZoneResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
class DemandIndicatorWebIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    private static final ZoneId BOOKING_ZONE = ZoneId.of("America/Bogota");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void scopesDemandIndicatorsToTheProviderAndDestinationManager() {
        UUID managerId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID otherProviderId = UUID.randomUUID();
        UUID guideId = UUID.randomUUID();
        UUID touristId = UUID.randomUUID();

        CategoryResponse category = createCategory(managerId);
        ZoneResponse zone = createZone(managerId);
        SiteResponse site = createSite(managerId, category.categoryId(), zone.zoneId());
        ServiceResponse service = createService(
                providerId, category.categoryId(), zone.zoneId(), site.siteId());
        Instant bookedFor = nextBookingInstant();
        ReservationResponse reservation = createReservation(touristId, service.serviceId(), bookedFor, 2);
        assertThat(reservation.status().name()).isEqualTo("CONFIRMED");

        Instant from = Instant.now().minusSeconds(86_400);
        Instant to = Instant.now().plusSeconds(7 * 86_400);

        DemandIndicatorsResponse providerIndicators = indicators(providerId, "SERVICE_PROVIDER", from, to);
        assertThat(providerIndicators.scope()).isEqualTo("PROVIDER");
        assertThat(providerIndicators.summary().reservations()).isEqualTo(1);
        assertThat(providerIndicators.summary().people()).isEqualTo(2);
        assertThat(providerIndicators.summary().revenue()).isEqualByComparingTo("100000.00");
        assertThat(providerIndicators.services()).hasSize(1);
        assertThat(providerIndicators.services().getFirst().serviceId()).isEqualTo(service.serviceId());
        assertThat(providerIndicators.services().getFirst().occupancyRate()).isGreaterThan(0.0);
        assertThat(providerIndicators.sites()).hasSize(1);
        assertThat(providerIndicators.sites().getFirst().siteId()).isEqualTo(site.siteId());
        assertThat(providerIndicators.zones()).hasSize(1);
        assertThat(providerIndicators.zones().getFirst().zoneId()).isEqualTo(zone.zoneId());

        DemandIndicatorsResponse otherProvider = indicators(otherProviderId, "SERVICE_PROVIDER", from, to);
        assertThat(otherProvider.summary().reservations()).isZero();
        assertThat(otherProvider.services()).isEmpty();

        DemandIndicatorsResponse managerIndicators = indicators(managerId, "DESTINATION_MANAGER", from, to);
        assertThat(managerIndicators.scope()).isEqualTo("MANAGER");
        assertThat(managerIndicators.summary().reservations()).isEqualTo(1);
        assertThat(managerIndicators.sites()).hasSize(1);
        assertThat(managerIndicators.zones()).hasSize(1);

        clientFor(guideId, "GUIDE")
                .get().uri("/api/v1/demand-indicators")
                .exchange()
                .expectStatus().isForbidden();

        clientFor(guideId, "GUIDE")
                .post().uri("/api/v1/services")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();

        clientFor(touristId, "TOURIST")
                .get().uri("/api/v1/demand-indicators")
                .exchange()
                .expectStatus().isForbidden();

        clientFor(managerId, "DESTINATION_MANAGER")
                .get().uri("/api/v1/demand-indicators?from=" + from + "&to=" + to
                        + "&zoneId=" + UUID.randomUUID())
                .exchange()
                .expectStatus().isForbidden();

        clientFor(managerId, "DESTINATION_MANAGER")
                .get().uri("/api/v1/demand-indicators/report?from=" + from + "&to=" + to)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/csv")
                .expectBody(String.class)
                .value(csv -> {
                    assertThat(csv).contains("Resumen");
                    assertThat(csv).contains(service.name());
                });
    }

    private DemandIndicatorsResponse indicators(
            UUID userId,
            String role,
            Instant from,
            Instant to
    ) {
        return clientFor(userId, role)
                .get().uri("/api/v1/demand-indicators?from=" + from + "&to=" + to)
                .exchange()
                .expectStatus().isOk()
                .expectBody(DemandIndicatorsResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private CategoryResponse createCategory(UUID adminId) {
        return clientFor(adminId, "ADMIN")
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
        return clientFor(managerId, "DESTINATION_MANAGER")
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
        return clientFor(managerId, "DESTINATION_MANAGER")
                .post().uri("/api/v1/sites")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name":"Sitio de demanda",
                          "description":"Sitio de prueba",
                          "categoryId":"%s",
                          "zoneId":"%s",
                          "latitude":10.39,
                          "longitude":-75.47,
                          "photosUrls":[],
                          "maxCapacity":50,
                          "availability":[{"dayOfWeek":"MONDAY","opensAt":"09:00:00","closesAt":"17:00:00"}],
                          "status":"PUBLISHED"
                        }
                        """.formatted(categoryId, zoneId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(SiteResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ServiceResponse createService(
            UUID providerId,
            UUID categoryId,
            UUID zoneId,
            UUID siteId
    ) {
        return clientFor(providerId, "SERVICE_PROVIDER")
                .post().uri("/api/v1/services")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "name":"Caminata guiada",
                          "description":"Servicio de prueba",
                          "type":"TOUR",
                          "categoryId":"%s",
                          "siteId":"%s",
                          "zoneId":"%s",
                          "latitude":10.39,
                          "longitude":-75.47,
                          "price":50000,
                          "maxCapacity":10,
                          "availability":[{"dayOfWeek":"MONDAY","opensAt":"09:00:00","closesAt":"17:00:00"}],
                          "photosUrls":[],
                          "cancellationPolicy":"48 horas",
                          "status":"PUBLISHED"
                        }
                        """.formatted(categoryId, siteId, zoneId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ServiceResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private ReservationResponse createReservation(
            UUID touristId,
            UUID serviceId,
            Instant bookedFor,
            int people
    ) {
        return clientFor(touristId, "TOURIST")
                .post().uri("/api/v1/reservations")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "bookedFor":"%s",
                          "notes":"Reserva de prueba",
                          "items":[{"serviceId":"%s","people":%d}]
                        }
                        """.formatted(bookedFor, serviceId, people))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ReservationResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private static Instant nextBookingInstant() {
        ZonedDateTime nextMonday = ZonedDateTime.now(BOOKING_ZONE)
                .plusDays(1)
                .with(DayOfWeek.MONDAY)
                .withHour(10)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        if (!nextMonday.toLocalTime().equals(LocalTime.of(10, 0))) {
            nextMonday = nextMonday.plusWeeks(1);
        }
        while (!nextMonday.isAfter(ZonedDateTime.now(BOOKING_ZONE))) {
            nextMonday = nextMonday.plusWeeks(1);
        }
        return nextMonday.toInstant();
    }

    private WebTestClient clientFor(UUID userId, String role) {
        return webTestClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + TestJwtSupport.bearer(jwtEncoder, userId, role, "Test User"))
                .build();
    }
}
