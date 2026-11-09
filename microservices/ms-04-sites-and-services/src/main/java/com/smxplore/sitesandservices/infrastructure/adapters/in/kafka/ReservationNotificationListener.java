package com.smxplore.sitesandservices.infrastructure.adapters.in.kafka;

import com.smxplore.sitesandservices.application.notification.NotificationCreateService;
import com.smxplore.sitesandservices.domain.exception.InvalidNotificationEventException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MS04-11: los eventos que no cumplen el contrato (productor, version o forma) no se procesan:
 * se envian al topic DLQ configurado y se descartan sin efectos laterales.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "app.notifications.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ReservationNotificationListener {

    private final ReservationChangedEventDecoder decoder;
    private final NotificationCreateService notificationCreateService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String deadLetterTopic;

    public ReservationNotificationListener(
            ReservationChangedEventDecoder decoder,
            NotificationCreateService notificationCreateService,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.notifications.dlq-topic:kafka.dlq.bookings.reservation.changed}")
            String deadLetterTopic
    ) {
        this.decoder = decoder;
        this.notificationCreateService = notificationCreateService;
        this.kafkaTemplate = kafkaTemplate;
        this.deadLetterTopic = deadLetterTopic;
    }

    @KafkaListener(
            id = "ms-04-reservation-notifications",
            topics = ReservationChangedEventDecoder.EVENT_TYPE,
            groupId = "${spring.kafka.consumer.group-id:ms-04-notifications}"
    )
    public void onReservationChanged(ConsumerRecord<String, String> record) {
        try {
            decoder.decode(record.value())
                    .ifPresent(message -> notificationCreateService.handle(message)
                            .block(Duration.ofSeconds(10)));
            log.debug("Processed booking notification event {}", record.key());
        } catch (InvalidNotificationEventException exception) {
            sendToDeadLetterTopic(record, exception);
        }
    }

    private void sendToDeadLetterTopic(
            ConsumerRecord<String, String> record,
            InvalidNotificationEventException exception
    ) {
        log.warn(
                "Booking event {} is invalid; sending it to {}: {}",
                record.key(),
                deadLetterTopic,
                exception.getMessage());
        kafkaTemplate.send(deadLetterTopic, record.key(), record.value())
                .exceptionally(error -> {
                    log.error(
                            "Could not send invalid booking event {} to {}",
                            record.key(),
                            deadLetterTopic,
                            error);
                    return null;
                });
    }
}
