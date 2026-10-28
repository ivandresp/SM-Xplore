package com.smxplore.sitesandservices.infrastructure.adapters.out.kafka;

import com.smxplore.sitesandservices.AbstractMongoPostgresIntegrationTest;
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
import com.smxplore.sitesandservices.infrastructure.config.OutboxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationOutboxClaimIntegrationTests extends AbstractMongoPostgresIntegrationTest {

    private static final ZoneId BOOKING_ZONE = ZoneId.of("America/Bogota");

    @Autowired
    private ServiceRepositoryPort serviceRepository;

    @Autowired
    private CreateReservationUseCase createReservationUseCase;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private OutboxProperties properties;

    @Autowired
    private OutboxMetrics metrics;

    @Autowired
    private Clock clock;

    @Test
    void twoRelayInstancesPublishEachReservationEventOnlyOnce() {
        Service service = serviceRepository.save(publishedService()).block();
        assertThat(service).isNotNull();
        Reservation draft = Reservation.builder()
                .bookedFor(nextMondayAtNoon())
                .items(List.of(ReservationItem.builder()
                        .serviceId(service.serviceId())
                        .people(2)
                        .build()))
                .build();
        Actor tourist = new Actor(UUID.randomUUID(), UserRole.TURISTA, "Turista claim");
        Reservation reservation = createReservationUseCase.handle(
                        draft,
                        "reservation-claim-" + UUID.randomUUID(),
                        tourist,
                        "reservation-claim-test"
                )
                .block();
        assertThat(reservation).isNotNull();

        CountingPublisher publisher = new CountingPublisher(reservation.reservationId());
        ReservationOutboxRelay first = new ReservationOutboxRelay(
                databaseClient, publisher, properties, metrics, clock);
        ReservationOutboxRelay second = new ReservationOutboxRelay(
                databaseClient, publisher, properties, metrics, clock);

        Mono.whenDelayError(first.relayOnce(), second.relayOnce())
                .block(Duration.ofSeconds(30));

        assertThat(publisher.count()).isEqualTo(1);
        String status = databaseClient.sql("""
                        SELECT status FROM reservation_outbox
                        WHERE aggregate_id = :aggregateId
                        """)
                .bind("aggregateId", reservation.reservationId())
                .map((row, metadata) -> row.get("status", String.class))
                .one()
                .block();
        assertThat(status).isEqualTo("PUBLISHED");
    }

    private Service publishedService() {
        Instant now = Instant.now();
        Service service = Service.builder()
                .serviceId(UUID.randomUUID())
                .providerId(UUID.randomUUID())
                .providerName("Prestadora claim")
                .siteId(UUID.randomUUID())
                .siteName("Sitio claim")
                .zoneId(UUID.randomUUID())
                .zoneName("Zona claim")
                .zoneStatus(ZoneStatus.ACTIVE)
                .categoryId(UUID.randomUUID())
                .categoryName("Categoría claim")
                .name("Servicio claim")
                .description("Servicio para probar el claim del outbox")
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

    private Instant nextMondayAtNoon() {
        LocalDate date = LocalDate.now(BOOKING_ZONE).plusWeeks(2);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        return ZonedDateTime.of(date, LocalTime.NOON, BOOKING_ZONE).toInstant();
    }

    private static final class CountingPublisher extends KafkaOutboxPublisher {

        private final UUID targetAggregateId;
        private final AtomicInteger count = new AtomicInteger();

        private CountingPublisher(UUID targetAggregateId) {
            super(null);
            this.targetAggregateId = targetAggregateId;
        }

        @Override
        public Mono<Void> publish(String eventType, String aggregateId, String eventJson) {
            if (targetAggregateId.toString().equals(aggregateId)) {
                count.incrementAndGet();
                return Mono.delay(Duration.ofMillis(250)).then();
            }
            return Mono.empty();
        }

        private int count() {
            return count.get();
        }
    }
}
