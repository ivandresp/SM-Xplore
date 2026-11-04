package com.smxplore.securityreports.infrastructure.adapters.out.kafka;

import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.OutboxEventDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class KafkaOutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public Mono<Void> publish(OutboxEventDocument event) {
        return Mono.fromFuture(kafkaTemplate.send(
                        event.getEventType(),
                        event.getAggregateId(),
                        event.getEventJson()))
                .then();
    }
}
