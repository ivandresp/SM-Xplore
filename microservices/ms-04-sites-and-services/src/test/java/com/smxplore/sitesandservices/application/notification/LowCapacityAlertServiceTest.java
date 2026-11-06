package com.smxplore.sitesandservices.application.notification;

import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationType;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationItem;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import com.smxplore.sitesandservices.domain.ports.out.NotificationDeliveryPort;
import com.smxplore.sitesandservices.domain.ports.out.NotificationRepositoryPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LowCapacityAlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");
    private static final Instant BOOKED_FOR = Instant.parse("2026-11-12T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID EVENT_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
    private static final UUID SERVICE_ID = UUID.fromString("44444444-4444-4444-4444-444444444401");
    private static final UUID PROVIDER_ID = UUID.fromString("55555555-5555-5555-5555-555555555501");
    private static final UUID MANAGER_ID = UUID.fromString("77777777-7777-7777-7777-777777777701");

    private final RecordingNotificationRepository repository = new RecordingNotificationRepository();
    private final RecordingDelivery delivery = new RecordingDelivery();
    private LowCapacityPolicy policy = new LowCapacityPolicy(true, 20);

    @Test
    void alertsTheProviderAndTheManagerWhenTheThresholdIsCrossed() {
        LowCapacityAlertService service = service();

        StepVerifier.create(service.handle(
                reservation(), EVENT_ID, List.of(snapshot(10, 2, 1)))).verifyComplete();

        assertThat(repository.saved).hasSize(2);
        assertThat(repository.saved).allSatisfy(notification -> {
            assertThat(notification.type()).isEqualTo(NotificationType.LOW_CAPACITY_ALERT);
            assertThat(notification.title()).isEqualTo("Alerta de baja capacidad");
            assertThat(notification.message()).contains("Snorkel en Playa Blanca")
                    .contains("1 de 10 plazas")
                    .contains("10%");
        });
        assertThat(repository.saved)
                .extracting(Notification::recipientRole)
                .containsExactlyInAnyOrder(UserRole.SERVICE_PROVIDER, UserRole.DESTINATION_MANAGER);
        assertThat(delivery.delivered).hasSize(2);
    }

    @Test
    void staysSilentWhenTheReservationDoesNotCrossTheThreshold() {
        LowCapacityAlertService service = service();

        StepVerifier.create(service.handle(
                reservation(), EVENT_ID, List.of(snapshot(10, 3, 2)))).verifyComplete();

        assertThat(repository.saved).isEmpty();
        assertThat(delivery.delivered).isEmpty();
    }

    @Test
    void staysSilentWhenTheCapacityWasAlreadyBelowTheThreshold() {
        LowCapacityAlertService service = service();

        StepVerifier.create(service.handle(
                reservation(), EVENT_ID, List.of(snapshot(10, 1, 0)))).verifyComplete();

        assertThat(repository.saved).isEmpty();
    }

    @Test
    void staysSilentWhenTheFeatureIsDisabled() {
        policy = new LowCapacityPolicy(false, 20);
        LowCapacityAlertService service = service();

        StepVerifier.create(service.handle(
                reservation(), EVENT_ID, List.of(snapshot(10, 2, 1)))).verifyComplete();

        assertThat(repository.saved).isEmpty();
    }

    @Test
    void sendsOneNotificationWhenProviderAndManagerAreTheSamePerson() {
        LowCapacityAlertService service = service();

        StepVerifier.create(service.handle(
                reservation(), EVENT_ID, List.of(snapshot(10, 2, 1, PROVIDER_ID, PROVIDER_ID))))
                .verifyComplete();

        assertThat(repository.saved).hasSize(1);
        assertThat(repository.saved.getFirst().recipientRole())
                .isEqualTo(UserRole.SERVICE_PROVIDER);
    }

    @Test
    void crossesThresholdOnlyWhenMovingFromAtOrAboveToBelow() {
        assertThat(LowCapacityAlertService.crossesThreshold(10, 2, 1, 20)).isTrue();
        assertThat(LowCapacityAlertService.crossesThreshold(10, 3, 2, 20)).isFalse();
        assertThat(LowCapacityAlertService.crossesThreshold(10, 1, 0, 20)).isFalse();
        assertThat(LowCapacityAlertService.crossesThreshold(10, 10, 9, 20)).isFalse();
        // Umbral redondeado hacia arriba: 20% de 7 = 1.4 -> 2, y se acota a [1, capacity].
        assertThat(LowCapacityAlertService.crossesThreshold(7, 2, 1, 20)).isTrue();
        assertThat(LowCapacityAlertService.crossesThreshold(4, 4, 3, 25)).isFalse();
    }

    private LowCapacityAlertService service() {
        return new LowCapacityAlertService(
                repository, List.of(delivery), policy, CLOCK);
    }

    private LowCapacitySnapshot snapshot(int capacity, int before, int after) {
        return snapshot(capacity, before, after, PROVIDER_ID, MANAGER_ID);
    }

    private LowCapacitySnapshot snapshot(
            int capacity,
            int before,
            int after,
            UUID providerId,
            UUID managerId
    ) {
        return new LowCapacitySnapshot(
                SERVICE_ID,
                "Snorkel en Playa Blanca",
                providerId,
                managerId,
                capacity,
                before,
                after,
                BOOKED_FOR);
    }

    private Reservation reservation() {
        ReservationItem item = ReservationItem.builder()
                .reservationItemId(UUID.randomUUID())
                .serviceId(SERVICE_ID)
                .serviceName("Snorkel en Playa Blanca")
                .providerId(PROVIDER_ID)
                .providerName("Caribe Tours")
                .serviceType("ACTIVITY")
                .unitPrice(new BigDecimal("80000.00"))
                .people(2)
                .total(new BigDecimal("160000.00"))
                .position(0)
                .build();
        return Reservation.builder()
                .reservationId(UUID.fromString("3ab2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"))
                .touristId(UUID.fromString("66666666-6666-6666-6666-666666666601"))
                .touristName("Luis Saumeth")
                .status(ReservationStatus.CONFIRMED)
                .total(new BigDecimal("160000.00"))
                .bookedFor(BOOKED_FOR)
                .items(List.of(item))
                .createdAt(NOW)
                .updatedAt(NOW)
                .confirmedAt(NOW)
                .build();
    }

    private static final class RecordingNotificationRepository implements NotificationRepositoryPort {

        private final List<Notification> saved = new ArrayList<>();

        @Override
        public Mono<Boolean> saveIfAbsent(Notification notification) {
            saved.add(notification);
            return Mono.just(true);
        }

        @Override
        public Mono<Notification> findById(UUID notificationId) {
            return Mono.empty();
        }

        @Override
        public Mono<com.smxplore.sitesandservices.domain.model.common.PageResult<Notification>>
                findAll(com.smxplore.sitesandservices.domain.model.notification.NotificationFilter filter) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> markRead(UUID notificationId, Instant readAt) {
            return Mono.empty();
        }
    }

    private static final class RecordingDelivery implements NotificationDeliveryPort {

        private final List<Notification> delivered = new ArrayList<>();

        @Override
        public Mono<Void> deliver(Notification notification) {
            delivered.add(notification);
            return Mono.empty();
        }
    }
}
