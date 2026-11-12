package com.smxplore.recommendations.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MS05-11: the startup warmup is enabled by default and only needs a positive timeout. A bad
 * timeout must fail fast, while a disabled warmup is a valid configuration.
 */
class RecommendationWarmupPropertiesTest {

    @Test
    void acceptsTheDefaultConfiguration() {
        RecommendationWarmupProperties properties = new RecommendationWarmupProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void acceptsADisabledWarmupWithAPositiveTimeout() {
        RecommendationWarmupProperties properties = new RecommendationWarmupProperties();
        properties.setEnabled(false);
        properties.setTimeout(Duration.ofSeconds(5));

        assertThatCode(properties::validate).doesNotThrowAnyException();
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void rejectsAZeroTimeout() {
        RecommendationWarmupProperties properties = new RecommendationWarmupProperties();
        properties.setTimeout(Duration.ZERO);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.warmup.timeout must be positive");
    }

    @Test
    void rejectsANegativeTimeout() {
        RecommendationWarmupProperties properties = new RecommendationWarmupProperties();
        properties.setTimeout(Duration.ofMillis(-1));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.warmup.timeout must be positive");
    }

    @Test
    void rejectsAMissingTimeout() {
        RecommendationWarmupProperties properties = new RecommendationWarmupProperties();
        properties.setTimeout(null);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.warmup.timeout must be positive");
    }
}
