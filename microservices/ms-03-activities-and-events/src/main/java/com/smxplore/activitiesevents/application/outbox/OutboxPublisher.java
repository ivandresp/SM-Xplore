package com.smxplore.activitiesevents.application.outbox;

import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Enqueues the event of a write that lives in the same PostgreSQL database.
 *
 * <p>The enqueue is part of the caller's transaction: a failure propagates and rolls the write
 * back, so the row and its event are always both present or both absent. The use cases that use
 * this publisher are wrapped in a {@code TransactionalOperator} by their configuration (see
 * {@code ActivityUseCaseConfiguration}/{@code EventUseCaseConfiguration}); without that wrapper
 * the enqueue would be an independent statement and a failure would leave an event-less row.
 *
 * <p>Reviews cannot use this path: their entity lives in MongoDB and the outbox in PostgreSQL, so
 * {@code ReviewEventPublisher} still enqueues after the save and logs a failure instead of failing
 * the write.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxPort outbox;

    public <T> Mono<T> publish(T saved, String eventType, UUID aggregateId, Mono<OutboxEvent> event) {
        return event
                .flatMap(outbox::enqueue)
                .doOnError(error -> log.error(
                        "Could not enqueue {} for {}; the transaction rolls back", eventType, aggregateId, error))
                .thenReturn(saved);
    }
}
