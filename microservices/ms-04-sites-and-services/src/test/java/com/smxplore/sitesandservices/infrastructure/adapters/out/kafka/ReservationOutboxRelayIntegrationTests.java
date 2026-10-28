package com.smxplore.sitesandservices.infrastructure.adapters.out.kafka;

import com.smxplore.sitesandservices.AbstractKafkaMongoPostgresIntegrationTest;
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ReservationOutboxRelayIntegrationTests extends AbstractKafkaMongoPostgresIntegrationTest {

    private static final ZoneId BOOKING_ZONE = ZoneId.of("America/Bogota");

    @Autowired
    private ServiceRepositoryPort serviceRepository;

    @Autowired
    private CreateReservationUseCase createReservationUseCase;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void relayPublishesReservationEventToKafka() {
        UUID providerId = UUID.randomUUID();
        Service service = serviceRepository.save(publishedService(providerId)).block();
        assertThat(service).isNotNull();

        Instant bookedFor = nextMondayAtNoon();
        Reservation draft = Reservation.builder()
                .bookedFor(bookedFor)
                .notes("Reserva Kafka")
                .items(List.of(ReservationItem.builder()
                        .serviceId(service.serviceId())
                        .people(2)
                        .build()))
                .build();
        Actor tourist = new Actor(
                UUID.randomUUID(), UserRole.TURISTA, "Turista Kafka");

        Reservation reservation = createReservationUseCase.handle(
                        draft,
                        "reservation-outbox-test",
                        tourist,
                        "reservation-outbox-test"
                )
                .block();

        assertThat(reservation).isNotNull();

        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of("bookings.reservation.changed"));
            ConsumerRecord<String, String> record = awaitRecord(
                    consumer,
                    reservation.reservationId().toString(),
                    Duration.ofSeconds(30)
            );

            assertThat(record.key()).isEqualTo(reservation.reservationId().toString());
            assertThat(record.value()).contains("\"eventType\":\"bookings.reservation.changed\"");
            assertThat(record.value()).contains("\"producer\":\"ms-04\"");
            assertThat(record.value()).contains("\"status\":\"CONFIRMED\"");
            assertThat(record.value()).contains("\"itemType\":\"SERVICE\"");
            assertThat(record.value()).contains(service.serviceId().toString());
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String status = databaseClient.sql("""
                            SELECT status FROM reservation_outbox
                            WHERE aggregate_id = :aggregateId
                            """)
                    .bind("aggregateId", reservation.reservationId())
                    .map((row, metadata) -> row.get("status", String.class))
                    .one()
                    .block();
            assertThat(status).isEqualTo("PUBLISHED");
        });
    }

    private Service publishedService(UUID providerId) {
        Instant now = Instant.now();
        Service service = Service.builder()
                .serviceId(UUID.randomUUID())
                .providerId(providerId)
                .providerName("Prestadora Kafka")
                .siteId(UUID.randomUUID())
                .siteName("Sitio Kafka")
                .zoneId(UUID.randomUUID())
                .zoneName("Zona Kafka")
                .zoneStatus(ZoneStatus.ACTIVE)
                .categoryId(UUID.randomUUID())
                .categoryName("Categoría Kafka")
                .name("Servicio Kafka")
                .description("Servicio para probar el outbox de reservas")
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

    private KafkaConsumer<String, String> consumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "reservation-outbox-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, String> awaitRecord(
            KafkaConsumer<String, String> consumer,
            String expectedKey,
            Duration timeout
    ) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (expectedKey.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No Kafka record with key " + expectedKey + " received in " + timeout);
    }

    private Instant nextMondayAtNoon() {
        LocalDate date = LocalDate.now(BOOKING_ZONE).plusWeeks(2);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        return ZonedDateTime.of(date, LocalTime.NOON, BOOKING_ZONE).toInstant();
    }
}
