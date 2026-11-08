package com.smxplore.users.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.domain.exception.TooManyLoginAttemptsException;
import com.smxplore.users.infrastructure.config.RateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryAccountAttemptLimiterTest {

    private static final String KEY = "login:ada@example.com";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final InMemoryAccountAttemptLimiter limiter =
            new InMemoryAccountAttemptLimiter(new RateLimitProperties(true, 30, 2, Duration.ofMinutes(15)), clock);

    @Test
    void blocksTheAccountAfterTheConfiguredFailures() {
        limiter.recordFailure(KEY).block();
        limiter.recordFailure(KEY).block();

        assertThatThrownBy(() -> limiter.ensureAllowed(KEY).block()).isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    void allowsTheAccountAgainAfterASuccessfulLogin() {
        limiter.recordFailure(KEY).block();
        limiter.recordFailure(KEY).block();
        limiter.clear(KEY).block();

        assertThatCode(() -> limiter.ensureAllowed(KEY).block()).doesNotThrowAnyException();
    }

    @Test
    void keepsTheCountersIndependentPerAccount() {
        limiter.recordFailure(KEY).block();
        limiter.recordFailure(KEY).block();

        assertThatCode(() -> limiter.ensureAllowed("login:otra@example.com").block())
                .doesNotThrowAnyException();
    }

    @Test
    void forgetsTheFailuresWhenTheWindowExpires() {
        limiter.recordFailure(KEY).block();
        limiter.recordFailure(KEY).block();

        clock.advance(Duration.ofMinutes(16));

        assertThatCode(() -> limiter.ensureAllowed(KEY).block()).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
