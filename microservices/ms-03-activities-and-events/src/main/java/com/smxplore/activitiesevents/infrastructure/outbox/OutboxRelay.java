package com.smxplore.activitiesevents.infrastructure.outbox;

import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Relays pending outbox rows to Kafka. Disabled unless OUTBOX_ENABLED=true. */
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxPort outbox;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    @Value("${app.outbox.enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    public void relay() {
        if (!enabled) {
            return;
        }
        var kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            return;
        }
        outbox.findPending(50)
                .concatMap(event -> Mono.fromFuture(kafkaTemplate.send(
                                event.eventType(), event.aggregateId().toString(), event.payload()))
                        .timeout(Duration.ofSeconds(10))
                        .then(outbox.markPublished(event.id()))
                        .onErrorResume(error -> outbox.markFailed(event.id(), error.getMessage())))
                .subscribe();
    }
}
