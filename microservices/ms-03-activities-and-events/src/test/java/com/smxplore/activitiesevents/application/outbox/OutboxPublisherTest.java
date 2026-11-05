package com.smxplore.activitiesevents.application.outbox;

import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La propiedad central del outbox transaccional: si el encolado falla, el publisher propaga el
 * error para que la transaccion revierta la escritura. Si volviera a tragarse el error, la fila
 * quedaria guardada sin su evento, que es justo el fallo que este cambio elimina.
 */
class OutboxPublisherTest {

    @Test
    void returnsTheSavedRowWhenTheEventIsEnqueued() {
        var outbox = new RecordingOutbox(null);
        var publisher = new OutboxPublisher(outbox);
        var saved = "activity";

        StepVerifier.create(publisher.publish(saved, "activities.activity.changed", UUID.randomUUID(),
                        Mono.just(event())))
                .assertNext(value -> assertThat(value).isEqualTo(saved))
                .verifyComplete();

        assertThat(outbox.enqueued).isEqualTo(1);
    }

    @Test
    void propagatesTheErrorWhenTheEnqueueFails() {
        var outbox = new RecordingOutbox(new IllegalStateException("outbox insert failed"));
        var publisher = new OutboxPublisher(outbox);

        StepVerifier.create(publisher.publish("activity", "activities.activity.changed", UUID.randomUUID(),
                        Mono.just(event())))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("outbox insert failed"))
                .verify();
    }

    @Test
    void propagatesTheErrorWhenThePayloadCannotBeBuilt() {
        var publisher = new OutboxPublisher(new RecordingOutbox(null));

        StepVerifier.create(publisher.publish("activity", "activities.activity.changed", UUID.randomUUID(),
                        Mono.error(new IllegalArgumentException("payload"))))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    private static OutboxEvent event() {
        return new OutboxEvent(UUID.randomUUID(), "activities.activity.changed", 2, UUID.randomUUID(),
                UUID.randomUUID(), "{}", Instant.now());
    }

    private static final class RecordingOutbox implements OutboxPort {

        private final RuntimeException failure;
        private int enqueued;

        private RecordingOutbox(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public Mono<Void> enqueue(OutboxEvent event) {
            enqueued++;
            return failure == null ? Mono.empty() : Mono.error(failure);
        }

        @Override
        public Flux<ClaimedEvent> claim(int limit, Duration lease, int maxAttempts, String workerId) {
            return Flux.empty();
        }

        @Override
        public Mono<Boolean> markPublished(UUID eventId, String workerId) {
            return Mono.just(Boolean.FALSE);
        }

        @Override
        public Mono<Void> markFailed(UUID eventId, String workerId, int maxAttempts, Duration backoff,
                                     String error) {
            return Mono.empty();
        }

        @Override
        public Mono<Long> purgeTerminalBefore(Instant cutoff) {
            return Mono.just(0L);
        }
    }
}
