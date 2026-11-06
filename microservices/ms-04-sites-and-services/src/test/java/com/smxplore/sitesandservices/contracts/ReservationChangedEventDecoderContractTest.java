package com.smxplore.sitesandservices.contracts;

import com.smxplore.sitesandservices.application.notification.NotificationCreateService;
import com.smxplore.sitesandservices.application.notification.ReservationChangedMessage;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationFilter;
import com.smxplore.sitesandservices.domain.model.notification.NotificationType;
import com.smxplore.sitesandservices.domain.ports.out.NotificationDeliveryPort;
import com.smxplore.sitesandservices.domain.ports.out.NotificationRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.adapters.in.kafka.ReservationChangedEventDecoder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato del consumidor: los fixtures publicados por ms-03 deben decodificarse con
 * {@link ReservationChangedEventDecoder} sin excepción y producir los destinatarios que
 * espera ms-04. Si cambia el contrato, este test (y el del productor) fallan juntos.
 */
class ReservationChangedEventDecoderContractTest {

    private static final Path EXAMPLES = Path.of(
            System.getProperty("contracts.dir", "../../contracts/events"), "examples");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-06T15:00:00Z"), ZoneOffset.UTC);

    private final ReservationChangedEventDecoder decoder =
            new ReservationChangedEventDecoder(new ObjectMapper());
    private final RecordingNotificationRepository repository = new RecordingNotificationRepository();
    private final NotificationCreateService service = new NotificationCreateService(
            repository, List.of(notification -> Mono.empty()), CLOCK);

    @Test
    void decodesTheVersionOneFixtureWithoutRecipientsBeyondTheTourist() throws Exception {
        ReservationChangedMessage message = decode("bookings.reservation.changed.v1.example.json");

        assertThat(message.status()).isEqualTo("CONFIRMED");
        assertThat(message.bookedFor()).isNull();
        assertThat(message.items()).hasSize(1);
        ReservationChangedMessage.Item item = message.items().getFirst();
        assertThat(item.itemType()).isEqualTo("EVENT");
        assertThat(item.people()).isEqualTo(2);
        assertThat(item.total()).isEqualByComparingTo("200000");
        assertThat(item.managerId()).isNull();

        StepVerifier.create(service.handle(message)).verifyComplete();

        assertThat(repository.saved).hasSize(1);
        Notification notification = repository.saved.getFirst();
        assertThat(notification.type()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);
        assertThat(notification.recipientRole()).isEqualTo(UserRole.TOURIST);
        assertThat(notification.recipientId()).isEqualTo(message.touristId());
        assertThat(notification.message()).contains("el evento reservado");
    }

    @Test
    void decodesTheVersionTwoFixtureAndNotifiesTheGuideOfTheActivity() throws Exception {
        ReservationChangedMessage message = decode("bookings.reservation.changed.v2.example.json");

        assertThat(message.bookedFor()).isEqualTo(Instant.parse("2026-11-12T15:00:00Z"));
        assertThat(message.items()).hasSize(1);
        ReservationChangedMessage.Item item = message.items().getFirst();
        assertThat(item.itemType()).isEqualTo("ACTIVITY");
        assertThat(item.itemName()).isEqualTo("Snorkel en Playa Blanca");
        assertThat(item.people()).isEqualTo(2);
        assertThat(item.total()).isEqualByComparingTo("160000");
        UUID guideId = UUID.fromString("55555555-5555-5555-5555-555555555501");
        assertThat(item.guideId()).isEqualTo(guideId);
        assertThat(item.providerId()).isNull();
        assertThat(item.managerId()).isNull();

        StepVerifier.create(service.handle(message)).verifyComplete();

        assertThat(repository.saved)
                .extracting(Notification::recipientRole)
                .containsExactlyInAnyOrder(UserRole.TOURIST, UserRole.GUIDE);
        Notification guideNotification = repository.saved.stream()
                .filter(notification -> notification.recipientRole() == UserRole.GUIDE)
                .findFirst()
                .orElseThrow();
        assertThat(guideNotification.recipientId()).isEqualTo(guideId);
        assertThat(guideNotification.message()).contains("Snorkel en Playa Blanca");
    }

    private ReservationChangedMessage decode(String fixture) throws Exception {
        String json = Files.readString(EXAMPLES.resolve(fixture));
        return decoder.decode(json).orElseThrow(
                () -> new AssertionError("Decoder rejected the fixture " + fixture));
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
        public Mono<PageResult<Notification>> findAll(NotificationFilter filter) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> markRead(UUID notificationId, Instant readAt) {
            return Mono.empty();
        }
    }
}
