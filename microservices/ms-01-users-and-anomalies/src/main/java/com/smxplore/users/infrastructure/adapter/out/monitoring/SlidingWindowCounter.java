package com.smxplore.users.infrastructure.adapter.out.monitoring;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Contador de eventos por segundo con una ventana máxima de una hora.
 *
 * <p>La ventana se implementa con 3600 buckets; cada bucket recuerda el segundo al que pertenece para ignorar los
 * valores viejos cuando el índice circular se reutiliza. Es una aproximación suficiente para detectar picos y no
 * pretende ser un contador exacto bajo concurrencia extrema.
 */
final class SlidingWindowCounter {

    private static final int BUCKETS = 3600;

    private final AtomicLongArray epochs = new AtomicLongArray(BUCKETS);
    private final AtomicLongArray counts = new AtomicLongArray(BUCKETS);
    private final Clock clock;

    SlidingWindowCounter(Clock clock) {
        this.clock = clock;
    }

    void increment() {
        long epoch = clock.instant().getEpochSecond();
        int index = (int) Math.floorMod(epoch, BUCKETS);
        if (epochs.get(index) != epoch) {
            epochs.set(index, epoch);
            counts.set(index, 0);
        }
        counts.incrementAndGet(index);
    }

    long countSince(Instant since) {
        long sinceEpoch = since.getEpochSecond();
        long nowEpoch = clock.instant().getEpochSecond();
        long total = 0;
        for (int index = 0; index < BUCKETS; index++) {
            long epoch = epochs.get(index);
            if (epoch >= sinceEpoch && epoch <= nowEpoch) {
                total += counts.get(index);
            }
        }
        return total;
    }
}
