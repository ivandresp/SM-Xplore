package com.smxplore.sitesandservices.application.notification;

import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationType;
import com.smxplore.sitesandservices.domain.ports.out.NotificationDeliveryPort;
import com.smxplore.sitesandservices.domain.ports.out.NotificationRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationCreateServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-05T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private NotificationRepositoryPort notificationRepository;
    @Mock
    private NotificationDeliveryPort notificationDelivery;

    @Test
    void notifiesTouristGuideAndManagerForActivityAndEventReservations() {
        var touristId = UUID.randomUUID();
        var guideId = UUID.randomUUID();
        var managerId = UUID.randomUUID();
        var message = new ReservationChangedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                touristId,
                Instant.parse("2026-11-12T15:00:00Z"),
                "CONFIRMED",
                List.of(
                        new ReservationChangedMessage.Item("ACTIVITY", UUID.randomUUID(),
                                "Snorkel", null, guideId, null, null, 2, BigDecimal.TEN),
                        new ReservationChangedMessage.Item("EVENT", UUID.randomUUID(),
                                "Concierto", null, null, managerId, null, 3, BigDecimal.ONE)));

        given(notificationRepository.saveIfAbsent(any())).willReturn(Mono.just(true));
        given(notificationDelivery.deliver(any())).willReturn(Mono.empty());

        var service = new NotificationCreateService(notificationRepository, notificationDelivery, CLOCK);
        StepVerifier.create(service.handle(message)).verifyComplete();

        var captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should(org.mockito.Mockito.times(3))
                .saveIfAbsent(captor.capture());
        var notifications = captor.getAllValues();
        assertThat(notifications).extracting(Notification::recipientId)
                .containsExactlyInAnyOrder(touristId, guideId, managerId);
        assertThat(notifications).extracting(Notification::recipientRole)
                .containsExactlyInAnyOrder(UserRole.TOURIST, UserRole.GUIDE, UserRole.DESTINATION_MANAGER);
        assertThat(notifications).extracting(Notification::type)
                .containsOnly(NotificationType.RESERVATION_CONFIRMED);
        assertThat(notifications).allSatisfy(notification ->
                assertThat(notification.message()).contains("Snorkel", "Concierto"));
    }

    @Test
    void createsANoShowNotification() {
        var touristId = UUID.randomUUID();
        var guideId = UUID.randomUUID();
        var message = new ReservationChangedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                touristId,
                Instant.parse("2026-11-12T15:00:00Z"),
                "NO_SHOW",
                List.of(new ReservationChangedMessage.Item("ACTIVITY", UUID.randomUUID(),
                        "Snorkel", null, guideId, null, null, 2, BigDecimal.TEN)));
        given(notificationRepository.saveIfAbsent(any())).willReturn(Mono.just(true));
        given(notificationDelivery.deliver(any())).willReturn(Mono.empty());

        var service = new NotificationCreateService(notificationRepository, notificationDelivery, CLOCK);
        StepVerifier.create(service.handle(message)).verifyComplete();

        var captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should(org.mockito.Mockito.times(2))
                .saveIfAbsent(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::type)
                .containsOnly(NotificationType.RESERVATION_NO_SHOW);
    }
}
