package com.smxplore.users.support;

import com.smxplore.users.application.port.out.LoginAttemptRepository;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.policy.LoginLockoutPolicy;
import java.time.Duration;
import java.time.Instant;
import reactor.core.publisher.Mono;

public final class FakeLoginAttemptRepository implements LoginAttemptRepository {

    @Override
    public Mono<Duration> lockRemaining(Email email, Instant now) {
        return Mono.just(Duration.ZERO);
    }

    @Override
    public Mono<Duration> recordFailure(Email email, Instant now, LoginLockoutPolicy policy) {
        return Mono.just(Duration.ZERO);
    }

    @Override
    public Mono<Void> clear(Email email) {
        return Mono.empty();
    }

    @Override
    public Mono<Long> deleteLastFailedBefore(Instant cutoff) {
        return Mono.just(0L);
    }
}
