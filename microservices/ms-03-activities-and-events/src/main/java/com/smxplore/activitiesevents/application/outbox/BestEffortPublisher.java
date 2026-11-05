package com.smxplore.activitiesevents.application.outbox;

import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Enqueues the event of a row that is already saved.
 *
 * <p>The enqueue is best effort: if it fails the error is logged and the caller still returns the
 * saved row, because the user's write already succeeded. Activities, events and the outbox all live
 * in the same PostgreSQL database, so this could be made atomic with the transactional wrapper the
 * reservations use ({@code TransactionalCreateReservationUseCase}); it is not, to keep the write
 * paths symmetrical with the reviews, which also enqueue after saving.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BestEffortPublisher {

    private final OutboxPort outbox;

    public <T> Mono<T> publish(T saved, String eventType, UUID aggregateId, Mono<OutboxEvent> event) {
        return event
                .flatMap(outbox::enqueue)
                .thenReturn(saved)
                .onErrorResume(error -> {
                    log.error("Could not enqueue {} for {}", eventType, aggregateId, error);
                    return Mono.just(saved);
                });
    }
}
