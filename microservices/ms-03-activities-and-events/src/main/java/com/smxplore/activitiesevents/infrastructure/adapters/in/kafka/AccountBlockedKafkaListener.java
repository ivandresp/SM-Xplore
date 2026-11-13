package com.smxplore.activitiesevents.infrastructure.adapters.in.kafka;

import com.smxplore.activitiesevents.application.accounts.UnpublishGuideActivitiesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Consume el bloqueo de cuentas de ms-01 y despublica las actividades del guia. */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.events.account-consumer.enabled", havingValue = "true")
public class AccountBlockedKafkaListener {

    private final UserAccountBlockedEventDecoder decoder;
    private final UnpublishGuideActivitiesUseCase useCase;

    @Value("${app.events.account-consumer.timeout:PT10S}")
    private Duration timeout;

    @KafkaListener(
            id = "ms-03-account-blocked",
            topics = UserAccountBlockedEventDecoder.EVENT_TYPE,
            groupId = "${app.events.account-consumer.group:ms-03-account-blocked}")
    public void onAccountBlocked(ConsumerRecord<String, String> record) {
        var guideId = decoder.decode(record.value());
        if (guideId.isEmpty()) {
            return;
        }
        var unpublished = useCase.handle(guideId.get()).block(timeout);
        log.info("Guide {} blocked: {} activities unpublished", guideId.get(), unpublished);
    }
}
