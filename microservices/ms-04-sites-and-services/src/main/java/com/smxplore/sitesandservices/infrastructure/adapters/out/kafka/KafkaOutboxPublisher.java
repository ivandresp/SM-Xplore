package com.smxplore.sitesandservices.infrastructure.adapters.out.kafka;

import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.OutboxEventDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class KafkaOutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public Mono<Void> publish(OutboxEventDocument event) {
        return publish(event.getEventType(), event.getAggregateId(), event.getEventJson());
    }

    public Mono<Void> publish(String eventType, String aggregateId, String eventJson) {
        return Mono.fromFuture(kafkaTemplate.send(eventType, aggregateId, eventJson))
                .then();
    }
}
