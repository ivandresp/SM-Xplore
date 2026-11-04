package com.smxplore.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnomalyThresholdTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");

    @Test
    void defaultsMatchEachDetectionType() {
        assertThat(AnomalyThreshold.defaults(AnomalyType.SUSPICIOUS_ACCESS, NOW))
                .isEqualTo(new AnomalyThreshold(
                        AnomalyType.SUSPICIOUS_ACCESS, 5, 300, AnomalySeverity.HIGH, true, NOW, null));
        assertThat(AnomalyThreshold.defaults(AnomalyType.SERVICE_DOWNTIME, NOW))
                .isEqualTo(new AnomalyThreshold(
                        AnomalyType.SERVICE_DOWNTIME, 2, 60, AnomalySeverity.CRITICAL, true, NOW, null));
        assertThat(AnomalyThreshold.defaults(AnomalyType.REQUEST_SPIKE, NOW))
                .isEqualTo(new AnomalyThreshold(
                        AnomalyType.REQUEST_SPIKE, 300, 60, AnomalySeverity.MEDIUM, true, NOW, null));
    }

    @Test
    void updateReplacesConfigurationValues() {
        AnomalyThreshold threshold = AnomalyThreshold.defaults(AnomalyType.SUSPICIOUS_ACCESS, NOW);
        Instant later = NOW.plusSeconds(60);

        AnomalyThreshold updated = threshold.update(10, 120, AnomalySeverity.CRITICAL, false, later, null);

        assertThat(updated.thresholdValue()).isEqualTo(10);
        assertThat(updated.windowSeconds()).isEqualTo(120);
        assertThat(updated.severity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.updatedAt()).isEqualTo(later);
    }

    @Test
    void rejectsThresholdOutsideAllowedRange() {
        assertThatThrownBy(() -> new AnomalyThreshold(
                        AnomalyType.SUSPICIOUS_ACCESS, 0, 300, AnomalySeverity.HIGH, true, NOW, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsWindowOutsideAllowedRange() {
        assertThatThrownBy(() -> new AnomalyThreshold(
                        AnomalyType.SUSPICIOUS_ACCESS, 5, 5, AnomalySeverity.HIGH, true, NOW, null))
                .isInstanceOf(ValidationException.class);
    }
}
