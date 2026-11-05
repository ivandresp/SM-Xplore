package com.smxplore.activitiesevents.domain.ports.repository;

import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OutboxPort {

    Mono<Void> enqueue(OutboxEvent event);

    Flux<OutboxEvent> findPending(int limit);

    Mono<Void> markPublished(UUID eventId);

    Mono<Void> markFailed(UUID eventId, String error);

    static OutboxPort noop() {
        return new OutboxPort() {
            @Override
            public Mono<Void> enqueue(OutboxEvent event) {
                return Mono.empty();
            }

            @Override
            public Flux<OutboxEvent> findPending(int limit) {
                return Flux.empty();
            }

            @Override
            public Mono<Void> markPublished(UUID eventId) {
                return Mono.empty();
            }

            @Override
            public Mono<Void> markFailed(UUID eventId, String error) {
                return Mono.empty();
            }
        };
    }
}
