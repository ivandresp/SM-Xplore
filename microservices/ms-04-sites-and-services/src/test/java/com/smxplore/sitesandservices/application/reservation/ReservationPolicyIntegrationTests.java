package com.smxplore.sitesandservices.application.reservation;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ReservationLimitException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationItem;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.domain.ports.in.CreateReservationUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = {
        "app.bookings.max-active-per-tourist=100",
        "app.bookings.max-creations-per-minute=2",
        "app.bookings.max-advance-days=30"
})
class ReservationPolicyIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    private static final ZoneId BOOKING_ZONE = ZoneId.of("America/Bogota");

    @Autowired
    private ServiceRepositoryPort serviceRepository;

    @Autowired
    private CreateReservationUseCase createReservationUseCase;

    @Test
    void enforcesTheCreationRateLimitPerTourist() {
        Service service = serviceRepository.save(publishedService()).block();
        assertThat(service).isNotNull();
        Actor tourist = new Actor(UUID.randomUUID(), UserRole.TURISTA, "Rate limit tourist");
        Instant bookedFor = nextMondayWithin(7);

        createReservationUseCase.handle(
                draft(service.serviceId(), bookedFor), "rate-1", tourist, "rate-test").block();
        createReservationUseCase.handle(
                draft(service.serviceId(), bookedFor), "rate-2", tourist, "rate-test").block();

        assertThatThrownBy(() -> createReservationUseCase.handle(
                draft(service.serviceId(), bookedFor), "rate-3", tourist, "rate-test").block())
                .isInstanceOf(ReservationLimitException.class)
                .hasMessageContaining("per minute");
    }

    @Test
    void rejectsReservationsBeyondTheConfiguredAdvanceWindow() {
        Service service = serviceRepository.save(publishedService()).block();
        assertThat(service).isNotNull();
        Actor tourist = new Actor(UUID.randomUUID(), UserRole.TURISTA, "Advance tourist");

        assertThatThrownBy(() -> createReservationUseCase.handle(
                draft(service.serviceId(), nextMondayWithin(60)),
                "advance-1",
                tourist,
                "advance-test"
        ).block())
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("30 days");
    }

    private Reservation draft(UUID serviceId, Instant bookedFor) {
        return Reservation.builder()
                .bookedFor(bookedFor)
                .items(List.of(ReservationItem.builder()
                        .serviceId(serviceId)
                        .people(1)
                        .build()))
                .build();
    }

    private Service publishedService() {
        Instant now = Instant.now();
        Service service = Service.builder()
                .serviceId(UUID.randomUUID())
                .providerId(UUID.randomUUID())
                .providerName("Prestadora policy")
                .siteId(UUID.randomUUID())
                .siteName("Sitio policy")
                .zoneId(UUID.randomUUID())
                .zoneName("Zona policy")
                .zoneStatus(ZoneStatus.ACTIVE)
                .categoryId(UUID.randomUUID())
                .categoryName("Categoría policy")
                .name("Servicio policy")
                .description("Servicio para probar límites")
                .type("RESTAURANT")
                .latitude(10.39)
                .longitude(-75.47)
                .price(new BigDecimal("50000.00"))
                .status(ContentStatus.PUBLISHED)
                .maxCapacity(100)
                .availability(List.of(new AvailabilitySlot(
                        DayOfWeek.MONDAY,
                        LocalTime.of(10, 0),
                        LocalTime.of(18, 0)
                )))
                .photosUrls(List.of())
                .cancellationPolicy("48 horas")
                .createdAt(now)
                .updatedAt(now)
                .build();
        service.validate();
        return service;
    }

    private Instant nextMondayWithin(int minimumDays) {
        LocalDate date = LocalDate.now(BOOKING_ZONE).plusDays(minimumDays);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        return ZonedDateTime.of(date, LocalTime.NOON, BOOKING_ZONE).toInstant();
    }
}
