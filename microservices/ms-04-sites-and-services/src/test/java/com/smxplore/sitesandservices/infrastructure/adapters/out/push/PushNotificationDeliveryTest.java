package com.smxplore.sitesandservices.infrastructure.adapters.out.push;

import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationType;
import com.smxplore.sitesandservices.infrastructure.config.NotificationPushProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PushNotificationDeliveryTest {

    private final NotificationPushProperties properties = new NotificationPushProperties();
    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final PushNotificationDelivery delivery =
            new PushNotificationDelivery(properties, registry);

    @Test
    void recordsTheAttemptWhenTheChannelIsEnabled() {
        properties.setEnabled(true);
        properties.setProvider("noop");

        StepVerifier.create(delivery.deliver(notification())).verifyComplete();

        assertThat(counter(PushNotificationDelivery.ATTEMPT_COUNTER)).isEqualTo(1.0);
        assertThat(counter(PushNotificationDelivery.SKIPPED_COUNTER)).isZero();
    }

    @Test
    void onlyRecordsTheSkipWhenTheChannelIsDisabled() {
        properties.setEnabled(false);

        StepVerifier.create(delivery.deliver(notification())).verifyComplete();

        assertThat(counter(PushNotificationDelivery.ATTEMPT_COUNTER)).isZero();
        assertThat(counter(PushNotificationDelivery.SKIPPED_COUNTER)).isEqualTo(1.0);
    }

    private double counter(String name) {
        var meter = registry.find(name).counter();
        return meter == null ? 0.0 : meter.count();
    }

    private Notification notification() {
        return Notification.builder()
                .notificationId(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .recipientRole(UserRole.SERVICE_PROVIDER)
                .reservationId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .type(NotificationType.RESERVATION_CONFIRMED)
                .title("Nueva reserva confirmada")
                .message("Recibiste una reserva de 2 personas para Snorkel.")
                .createdAt(Instant.parse("2026-11-05T15:00:00Z"))
                .build();
    }
}
