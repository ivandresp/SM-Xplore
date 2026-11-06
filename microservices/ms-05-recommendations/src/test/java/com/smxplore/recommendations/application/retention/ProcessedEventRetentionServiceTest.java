package com.smxplore.recommendations.application.retention;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.ports.out.ProcessedEventRetentionPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessedEventRetentionServiceTest {

    private static final Instant CUTOFF = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void deletesInBatchesUntilThePortReturnsFewerThanTheBatchSize() {
        FakeRetentionPort port = new FakeRetentionPort(2L, 2L, 1L);
        ProcessedEventRetentionService service = new ProcessedEventRetentionService(port);

        assertThat(service.handle(CUTOFF, 2, 10).block()).isEqualTo(5L);
        assertThat(port.calls).isEqualTo(3);
    }

    @Test
    void stopsWhenTheBatchBudgetIsExhausted() {
        FakeRetentionPort port = new FakeRetentionPort(2L, 2L, 2L);
        ProcessedEventRetentionService service = new ProcessedEventRetentionService(port);

        assertThat(service.handle(CUTOFF, 2, 2).block()).isEqualTo(4L);
        assertThat(port.calls).isEqualTo(2);
    }

    @Test
    void returnsZeroWhenThereIsNothingToDelete() {
        FakeRetentionPort port = new FakeRetentionPort(0L);
        ProcessedEventRetentionService service = new ProcessedEventRetentionService(port);

        assertThat(service.handle(CUTOFF, 100, 5).block()).isZero();
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void rejectsInvalidArguments() {
        ProcessedEventRetentionService service = new ProcessedEventRetentionService(new FakeRetentionPort());

        assertThatThrownBy(() -> service.handle(null, 10, 1))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("cutoff is required");
        assertThatThrownBy(() -> service.handle(CUTOFF, 0, 1))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("batchSize must be >= 1");
        assertThatThrownBy(() -> service.handle(CUTOFF, 10, 0))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("maxBatches must be >= 1");
    }

    private static final class FakeRetentionPort implements ProcessedEventRetentionPort {

        private final Deque<Long> batches = new ArrayDeque<>();
        private int calls;

        private FakeRetentionPort(Long... batches) {
            this.batches.addAll(java.util.List.of(batches));
        }

        @Override
        public Mono<Long> deleteProcessedBefore(Instant cutoff, int batchSize) {
            calls++;
            assertThat(cutoff).isEqualTo(CUTOFF);
            return Mono.just(batches.isEmpty() ? 0L : batches.poll());
        }
    }
}
