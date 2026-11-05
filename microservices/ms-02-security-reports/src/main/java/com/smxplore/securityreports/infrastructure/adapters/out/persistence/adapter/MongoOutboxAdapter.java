package com.smxplore.securityreports.infrastructure.adapters.out.persistence.adapter;

import com.smxplore.securityreports.domain.model.event.DomainEvent;
import com.smxplore.securityreports.domain.ports.out.OutboxPort;
import com.smxplore.securityreports.infrastructure.adapters.out.event.DomainEventJsonSerializer;
import com.smxplore.securityreports.infrastructure.adapters.out.event.OutboxStatus;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.OutboxEventDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class MongoOutboxAdapter implements OutboxPort {

    private final ReactiveMongoTemplate mongoTemplate;
    private final DomainEventJsonSerializer serializer;
    private final Clock clock;

    @Override
    public Mono<Void> append(DomainEvent event) {
        Instant now = Instant.now(clock);
        OutboxEventDocument document = OutboxEventDocument.builder()
                .id(event.eventId().toString())
                .eventType(event.eventType())
                .aggregateId(event.aggregateId().toString())
                .eventJson(serializer.serialize(event))
                .occurredAt(event.occurredAt())
                .status(OutboxStatus.PENDING.name())
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .build();
        return mongoTemplate.insert(document).then();
    }
}
