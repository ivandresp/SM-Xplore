package com.smxplore.sitesandservices.infrastructure.adapters.in.kafka;

import com.smxplore.sitesandservices.application.notification.NotificationCreateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.notifications.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReservationNotificationListener {

    private final ReservationChangedEventDecoder decoder;
    private final NotificationCreateService notificationCreateService;

    @KafkaListener(
            id = "ms-04-reservation-notifications",
            topics = ReservationChangedEventDecoder.EVENT_TYPE,
            groupId = "${spring.kafka.consumer.group-id:ms-04-notifications}"
    )
    public void onReservationChanged(ConsumerRecord<String, String> record) {
        decoder.decode(record.value())
                .ifPresent(message -> notificationCreateService.handle(message)
                        .block(Duration.ofSeconds(10)));
        log.debug("Processed booking notification event {}", record.key());
    }
}
