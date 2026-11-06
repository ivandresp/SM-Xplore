package com.smxplore.users.infrastructure.adapter.out.monitoring;

import com.smxplore.users.application.port.out.RequestRateSignal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class InMemoryRequestRateSignal implements RequestRateSignal {

    private final SlidingWindowCounter counter;

    public InMemoryRequestRateSignal(Clock clock) {
        this.counter = new SlidingWindowCounter(clock);
    }

    @Override
    public Mono<Void> record() {
        return Mono.fromRunnable(counter::increment);
    }

    @Override
    public Mono<Long> countSince(Instant since) {
        return Mono.fromCallable(() -> counter.countSince(since));
    }
}
