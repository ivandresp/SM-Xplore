package com.smxplore.activitiesevents.infrastructure.outbox;

import com.smxplore.activitiesevents.domain.model.outbox.OutboxEvent;
import com.smxplore.activitiesevents.domain.ports.repository.OutboxPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private ObjectProvider<KafkaTemplate<String, String>> templateProvider;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private FakeOutbox outbox;
    private OutboxProperties properties;
    private OutboxMetrics metrics;

    @BeforeEach
    void setUp() {
        outbox = new FakeOutbox();
        properties = new OutboxProperties();
        properties.setEnabled(true);
        properties.setBatchSize(10);
        properties.setMaxAttempts(3);
        properties.setInitialBackoff(Duration.ofSeconds(1));
        properties.setMaxBackoff(Duration.ofMinutes(1));
        metrics = new OutboxMetrics(new SimpleMeterRegistry());
    }

    @Test
    void publishesTheClaimedEventsAndMarksThem() {
        given(templateProvider.getIfAvailable()).willReturn(kafkaTemplate);
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));
        outbox.pending.add(event("activities.activity.changed"));
        outbox.pending.add(event("activities.event.changed"));

        StepVerifier.create(relay().relayOnce()).verifyComplete();

        assertThat(outbox.published).hasSize(2);
        assertThat(outbox.failed).isEmpty();
    }

    @Test
    void marksTheEventFailedWhenTheSendFails() {
        given(templateProvider.getIfAvailable()).willReturn(kafkaTemplate);
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        outbox.pending.add(event("activities.activity.changed"));

        StepVerifier.create(relay().relayOnce()).verifyComplete();

        assertThat(outbox.published).isEmpty();
        assertThat(outbox.failed).containsExactly(outbox.pending.getFirst().id());
    }

    @Test
    void aSynchronousSendFailureDoesNotAbandonTheRestOfTheBatch() {
        given(templateProvider.getIfAvailable()).willReturn(kafkaTemplate);
        var broken = event("activities.activity.changed");
        var healthy = event("activities.activity.changed");
        outbox.pending.add(broken);
        outbox.pending.add(healthy);
        // El primer send revienta antes de devolver el future (producer cerrado, serializacion).
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willThrow(new IllegalStateException("producer closed"))
                .willReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(relay().relayOnce()).verifyComplete();

        assertThat(outbox.failed).containsExactly(broken.id());
        assertThat(outbox.published).containsExactly(healthy.id());
    }

    @Test
    void doesNothingWithoutAKafkaTemplate() {
        given(templateProvider.getIfAvailable()).willReturn(null);

        StepVerifier.create(relay().relayOnce()).verifyComplete();

        verifyNoInteractions(kafkaTemplate);
        assertThat(outbox.claimed).isFalse();
    }

    @Test
    void doesNotMarkTheEventFailedWhenTheLeaseWasLost() {
        given(templateProvider.getIfAvailable()).willReturn(kafkaTemplate);
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));
        outbox.loseLease = true;
        outbox.pending.add(event("activities.activity.changed"));

        StepVerifier.create(relay().relayOnce()).verifyComplete();

        assertThat(outbox.failed).isEmpty();
        assertThat(outbox.published).isEmpty();
    }

    private OutboxRelay relay() {
        return new OutboxRelay(outbox, templateProvider, properties, metrics);
    }

    private static OutboxEvent event(String eventType) {
        return new OutboxEvent(UUID.randomUUID(), eventType, 2, UUID.randomUUID(), UUID.randomUUID(),
                "{}", Instant.now());
    }

    /** In-memory outbox: claims everything pending and records what the relay did. */
    private static final class FakeOutbox implements OutboxPort {

        private final List<OutboxEvent> pending = new ArrayList<>();
        private final List<UUID> published = new ArrayList<>();
        private final List<UUID> failed = new ArrayList<>();
        private boolean claimed;
        private boolean loseLease;

        @Override
        public Mono<Void> enqueue(OutboxEvent event) {
            pending.add(event);
            return Mono.empty();
        }

        @Override
        public Flux<ClaimedEvent> claim(int limit, Duration lease, int maxAttempts, String workerId) {
            claimed = true;
            return Flux.fromIterable(pending)
                    .map(event -> new ClaimedEvent(event, 0));
        }

        @Override
        public Mono<Boolean> markPublished(UUID eventId, String workerId) {
            if (loseLease) {
                return Mono.just(Boolean.FALSE);
            }
            published.add(eventId);
            return Mono.just(Boolean.TRUE);
        }

        @Override
        public Mono<Void> markFailed(UUID eventId, String workerId, int maxAttempts, Duration backoff, String error) {
            failed.add(eventId);
            return Mono.empty();
        }

        @Override
        public Mono<Long> purgeTerminalBefore(Instant cutoff) {
            return Mono.just(0L);
        }
    }
}
