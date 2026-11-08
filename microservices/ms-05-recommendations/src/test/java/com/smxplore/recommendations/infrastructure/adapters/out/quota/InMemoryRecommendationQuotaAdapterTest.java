package com.smxplore.recommendations.infrastructure.adapters.out.quota;

import com.smxplore.recommendations.domain.exception.RateLimitExceededException;
import com.smxplore.recommendations.infrastructure.config.RecommendationQuotaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for MS05-02: one tourist cannot turn every request into two graph queries.
 * The budget is a fixed window, so the tests drive the clock instead of sleeping.
 */
class InMemoryRecommendationQuotaAdapterTest {

    private static final UUID TOURIST = UUID.randomUUID();
    private static final UUID OTHER_TOURIST = UUID.randomUUID();

    private MutableClock clock;
    private RecommendationQuotaProperties properties;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2027-01-01T00:00:00Z"));
        properties = new RecommendationQuotaProperties();
        properties.setEnabled(true);
        properties.setMaxRequests(2);
        properties.setWindow(Duration.ofMinutes(1));
        properties.setMaxEntries(100);
    }

    @Test
    void allowsRequestsUpToTheConfiguredBudget() {
        InMemoryRecommendationQuotaAdapter adapter = new InMemoryRecommendationQuotaAdapter(properties, clock);

        assertThatCode(() -> {
            adapter.check(TOURIST);
            adapter.check(TOURIST);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsTheRequestThatExceedsTheBudgetWithARetryAfter() {
        InMemoryRecommendationQuotaAdapter adapter = new InMemoryRecommendationQuotaAdapter(properties, clock);
        adapter.check(TOURIST);
        adapter.check(TOURIST);

        assertThatThrownBy(() -> adapter.check(TOURIST))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Recommendation quota exceeded")
                .satisfies(error -> assertThat(((RateLimitExceededException) error).retryAfterSeconds())
                        .isGreaterThanOrEqualTo(1L));
    }

    @Test
    void resetsTheBudgetWhenTheWindowElapses() {
        InMemoryRecommendationQuotaAdapter adapter = new InMemoryRecommendationQuotaAdapter(properties, clock);
        properties.setMaxRequests(1);
        adapter.check(TOURIST);
        assertThatThrownBy(() -> adapter.check(TOURIST)).isInstanceOf(RateLimitExceededException.class);

        clock.advance(Duration.ofSeconds(61));

        assertThatCode(() -> adapter.check(TOURIST)).doesNotThrowAnyException();
    }

    @Test
    void tracksEachTouristSeparately() {
        properties.setMaxRequests(1);
        InMemoryRecommendationQuotaAdapter adapter = new InMemoryRecommendationQuotaAdapter(properties, clock);
        adapter.check(TOURIST);

        assertThatCode(() -> adapter.check(OTHER_TOURIST)).doesNotThrowAnyException();
    }

    @Test
    void bypassesTheCheckWhenTheQuotaIsDisabled() {
        properties.setEnabled(false);
        InMemoryRecommendationQuotaAdapter adapter = new InMemoryRecommendationQuotaAdapter(properties, clock);

        assertThatCode(() -> {
            for (int i = 0; i < 10; i++) {
                adapter.check(TOURIST);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsARequestWithoutATouristIdentity() {
        InMemoryRecommendationQuotaAdapter adapter = new InMemoryRecommendationQuotaAdapter(properties, clock);

        assertThatThrownBy(() -> adapter.check(null))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("identity is required");
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
