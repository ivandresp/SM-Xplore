package com.smxplore.recommendations.infrastructure.adapters.out.metrics;

import com.smxplore.recommendations.domain.model.recommendation.RecommendationSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerRecommendationMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerRecommendationMetrics metrics = new MicrometerRecommendationMetrics(registry);

    @Test
    void countsServedRecommendationsPerSource() {
        metrics.served(RecommendationSource.AFFINITY, 3);
        metrics.served(RecommendationSource.AFFINITY, 2);
        metrics.served(RecommendationSource.FALLBACK, 1);

        assertThat(registry.get("recommendations.served").tag("source", "AFFINITY").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("recommendations.served").tag("source", "FALLBACK").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("recommendations.items").summary().totalAmount()).isEqualTo(6.0);
        assertThat(registry.get("recommendations.items").summary().count()).isEqualTo(3);
    }

    @Test
    void countsCacheResults() {
        metrics.cacheHit();
        metrics.cacheHit();
        metrics.cacheMiss();

        assertThat(registry.get("recommendations.cache").tag("result", "hit").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("recommendations.cache").tag("result", "miss").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordsTheDiversityOfEveryAnswer() {
        metrics.diversity(2, 3);
        metrics.diversity(1, 1);

        assertThat(registry.get("recommendations.diversity.categories").summary().totalAmount())
                .isEqualTo(3.0);
        assertThat(registry.get("recommendations.diversity.sites").summary().totalAmount())
                .isEqualTo(4.0);
    }

    @Test
    void ignoresAnswersWithoutSource() {
        metrics.served(null, 4);

        assertThat(registry.find("recommendations.served").counters()).isEmpty();
        assertThat(registry.get("recommendations.items").summary().count()).isZero();
    }
}
