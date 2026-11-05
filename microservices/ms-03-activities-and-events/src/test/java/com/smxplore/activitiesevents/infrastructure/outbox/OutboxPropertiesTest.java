package com.smxplore.activitiesevents.infrastructure.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxPropertiesTest {

    @Test
    void backoffGrowsExponentiallyUntilTheCap() {
        var properties = new OutboxProperties();
        properties.setInitialBackoff(Duration.ofSeconds(1));
        properties.setMaxBackoff(Duration.ofMinutes(1));

        assertThat(properties.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.backoff(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.backoff(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(properties.backoff(30)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void rejectsACycleShorterThanTheWorstCaseBatch() {
        var properties = new OutboxProperties();
        properties.setBatchSize(10);
        properties.setSendTimeout(Duration.ofSeconds(5));
        properties.setCycleTimeout(Duration.ofSeconds(10));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle-timeout");
    }

    @Test
    void rejectsABackoffWindowThatShrinks() {
        var properties = new OutboxProperties();
        properties.setInitialBackoff(Duration.ofMinutes(2));
        properties.setMaxBackoff(Duration.ofMinutes(1));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backoff");
    }

    @Test
    void acceptsTheDefaults() {
        assertThat(new OutboxProperties().getBatchSize()).isPositive();
        new OutboxProperties().validate();
    }
}
