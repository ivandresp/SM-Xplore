package com.smxplore.sitesandservices.application.support;

import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationAvailabilityPolicyTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** Lunes 10:00 en Bogotá (UTC-5). */
    private static final Instant MONDAY_MORNING = Instant.parse("2026-11-02T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    private final ReservationAvailabilityPolicy policy = new ReservationAvailabilityPolicy(BOGOTA);

    @Test
    void reportsWhenTheSiteIsMissingOrNotPublished() {
        assertThat(policy.siteProblem(null))
                .isEqualTo(ReservationAvailabilityPolicy.SITE_NOT_AVAILABLE);
        assertThat(policy.siteProblem(site(ContentStatus.DRAFT)))
                .isEqualTo(ReservationAvailabilityPolicy.SITE_NOT_PUBLISHED);
        assertThat(policy.siteProblem(site(ContentStatus.PUBLISHED))).isNull();
    }

    @Test
    void selectsTheSlotThatCoversTheBookingAndExposesItsWindow() {
        AvailabilitySlot monday = new AvailabilitySlot(
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), 4);
        Service service = service(List.of(monday));

        AvailabilitySlot slot = policy.slotFor(service, MONDAY_MORNING);

        assertThat(slot).isEqualTo(monday);
        assertThat(policy.slotStart(MONDAY_MORNING, slot))
                .isEqualTo(Instant.parse("2026-11-02T14:00:00Z"));
        assertThat(policy.slotEnd(MONDAY_MORNING, slot))
                .isEqualTo(Instant.parse("2026-11-02T23:00:00Z"));
    }

    @Test
    void ignoresSlotsThatDoNotCoverTheBooking() {
        Service service = service(List.of(new AvailabilitySlot(
                DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(18, 0))));

        assertThat(policy.slotFor(service, MONDAY_MORNING)).isNull();
        assertThat(policy.availabilityProblem(service, MONDAY_MORNING))
                .isEqualTo(ReservationAvailabilityPolicy.OUTSIDE_SERVICE_AVAILABILITY);
    }

    @Test
    void allowsAnyTimeWhenTheServiceDeclaresNoSlots() {
        Service service = service(List.of());

        assertThat(policy.slotFor(service, MONDAY_MORNING)).isNull();
        assertThat(policy.availabilityProblem(service, MONDAY_MORNING)).isNull();
    }

    @Test
    void reportsTheUnpublishedServiceBeforeItsZone() {
        Service draft = service(List.of()).toBuilder()
                .status(ContentStatus.DRAFT)
                .build();
        assertThat(policy.availabilityProblem(draft, MONDAY_MORNING))
                .isEqualTo(ReservationAvailabilityPolicy.SERVICE_NOT_PUBLISHED);

        Service inactiveZone = service(List.of()).toBuilder()
                .zoneStatus(ZoneStatus.INACTIVE)
                .build();
        assertThat(policy.availabilityProblem(inactiveZone, MONDAY_MORNING))
                .isEqualTo(ReservationAvailabilityPolicy.ZONE_INACTIVE);
    }

    private static Service service(List<AvailabilitySlot> availability) {
        return Service.builder()
                .serviceId(UUID.randomUUID())
                .providerId(UUID.randomUUID())
                .providerName("Prestadora")
                .siteId(UUID.randomUUID())
                .siteName("Sitio")
                .zoneId(UUID.randomUUID())
                .zoneName("Zona")
                .zoneStatus(ZoneStatus.ACTIVE)
                .categoryId(UUID.randomUUID())
                .categoryName("Categoría")
                .name("Servicio")
                .type("RESTAURANT")
                .latitude(10.0)
                .longitude(-75.0)
                .price(BigDecimal.TEN)
                .status(ContentStatus.PUBLISHED)
                .maxCapacity(10)
                .availability(availability)
                .photosUrls(List.of())
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static Site site(ContentStatus status) {
        return Site.builder()
                .siteId(UUID.randomUUID())
                .managerId(UUID.randomUUID())
                .name("Sitio")
                .categoryId(UUID.randomUUID())
                .categoryName("Categoría")
                .zoneId(UUID.randomUUID())
                .zoneName("Zona")
                .zoneStatus(ZoneStatus.ACTIVE)
                .latitude(10.0)
                .longitude(-75.0)
                .status(status)
                .maxCapacity(10)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
