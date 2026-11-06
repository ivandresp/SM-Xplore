package com.smxplore.sitesandservices.application.reservation;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationItem;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationCompletionIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    @Autowired
    private ServiceRepositoryPort serviceRepository;

    @Autowired
    private ReservationRepositoryPort reservationRepository;

    @Autowired
    private ReservationCompletionService completionService;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void completesPastConfirmedReservationsAndPublishesTheEvent() {
        Service service = serviceRepository.save(publishedService()).block();
        assertThat(service).isNotNull();
        Instant now = Instant.now();
        Reservation reservation = reservation(service, now);

        reservationRepository.save(reservation).block();
        completionService.completeOnce().block();

        Reservation completed = reservationRepository.findById(reservation.reservationId())
                .block();
        assertThat(completed).isNotNull();
        assertThat(completed.status()).isEqualTo(ReservationStatus.COMPLETED);

        String eventJson = databaseClient.sql("""
                        SELECT event_json FROM reservation_outbox
                        WHERE aggregate_id = :aggregateId
                        """)
                .bind("aggregateId", reservation.reservationId())
                .map((row, metadata) -> row.get("event_json", String.class))
                .one()
                .block();
        assertThat(eventJson).isNotNull();
        assertThat(eventJson).contains("\"status\":\"COMPLETED\"");
    }

    private Reservation reservation(Service service, Instant now) {
        ReservationItem item = ReservationItem.builder()
                .reservationItemId(UUID.randomUUID())
                .serviceId(service.serviceId())
                .serviceName(service.name())
                .providerId(service.providerId())
                .providerName(service.providerName())
                .serviceType(service.type())
                .unitPrice(service.price())
                .people(2)
                .total(ReservationItem.calculateTotal(service.price(), 2))
                .position(0)
                .build();
        Reservation reservation = Reservation.builder()
                .reservationId(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .touristName("Turista completado")
                .status(ReservationStatus.CONFIRMED)
                .total(item.total())
                .bookedFor(now.minusSeconds(3_600))
                .items(List.of(item))
                .createdAt(now.minusSeconds(7_200))
                .updatedAt(now.minusSeconds(7_200))
                .confirmedAt(now.minusSeconds(7_200))
                .build();
        reservation.validate();
        return reservation;
    }

    private Service publishedService() {
        Instant now = Instant.now();
        Service service = Service.builder()
                .serviceId(UUID.randomUUID())
                .providerId(UUID.randomUUID())
                .providerName("Prestadora completion")
                .siteId(UUID.randomUUID())
                .siteName("Sitio completion")
                .zoneId(UUID.randomUUID())
                .zoneName("Zona completion")
                .zoneStatus(ZoneStatus.ACTIVE)
                .categoryId(UUID.randomUUID())
                .categoryName("Categoría completion")
                .name("Servicio completion")
                .description("Servicio para probar auto-completado")
                .type("RESTAURANT")
                .latitude(10.39)
                .longitude(-75.47)
                .price(new BigDecimal("50000.00"))
                .status(ContentStatus.PUBLISHED)
                .maxCapacity(20)
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
}
