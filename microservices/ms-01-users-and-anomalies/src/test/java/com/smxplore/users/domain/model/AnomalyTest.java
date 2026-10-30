package com.smxplore.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.domain.exception.AnomalyAlreadyResolvedException;
import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnomalyTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void detectedAnomalyStartsPending() {
        Anomaly anomaly =
                Anomaly.detect(AnomalyType.SUSPICIOUS_ACCESS, AnomalySeverity.HIGH, "Cinco inicios fallidos", NOW);

        assertThat(anomaly.id()).isNotNull();
        assertThat(anomaly.status()).isEqualTo(AnomalyStatus.DETECTED);
        assertThat(anomaly.severity()).isEqualTo(AnomalySeverity.HIGH);
        assertThat(anomaly.detectedAt()).isEqualTo(NOW);
        assertThat(anomaly.resolvedAt()).isNull();
        assertThat(anomaly.resolvedBy()).isNull();
    }

    @Test
    void classifyChangesSeverity() {
        Anomaly anomaly = Anomaly.detect(AnomalyType.REQUEST_SPIKE, AnomalySeverity.MEDIUM, "Pico de solicitudes", NOW);

        anomaly.classify(AnomalySeverity.CRITICAL);

        assertThat(anomaly.severity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(anomaly.status()).isEqualTo(AnomalyStatus.DETECTED);
    }

    @Test
    void resolveRecordsAdminDateAndNote() {
        Anomaly anomaly = Anomaly.detect(AnomalyType.SERVICE_DOWNTIME, AnomalySeverity.CRITICAL, "Servicio caído", NOW);
        Instant resolvedAt = NOW.plusSeconds(120);

        anomaly.resolve(ADMIN_ID, "  Reiniciado el servicio  ", resolvedAt);

        assertThat(anomaly.status()).isEqualTo(AnomalyStatus.RESOLVED);
        assertThat(anomaly.resolvedBy()).isEqualTo(ADMIN_ID);
        assertThat(anomaly.resolvedAt()).isEqualTo(resolvedAt);
        assertThat(anomaly.resolutionNote()).isEqualTo("Reiniciado el servicio");
    }

    @Test
    void resolvedAnomalyCannotBeResolvedAgain() {
        Anomaly anomaly = Anomaly.detect(AnomalyType.SUSPICIOUS_ACCESS, AnomalySeverity.HIGH, "Acceso sospechoso", NOW);
        anomaly.resolve(ADMIN_ID, null, NOW.plusSeconds(1));

        assertThatThrownBy(() -> anomaly.resolve(ADMIN_ID, null, NOW.plusSeconds(2)))
                .isInstanceOf(AnomalyAlreadyResolvedException.class);
    }

    @Test
    void blankDescriptionIsRejected() {
        assertThatThrownBy(() -> Anomaly.detect(AnomalyType.SUSPICIOUS_ACCESS, AnomalySeverity.HIGH, " ", NOW))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void oversizedResolutionNoteIsRejected() {
        Anomaly anomaly = Anomaly.detect(AnomalyType.SUSPICIOUS_ACCESS, AnomalySeverity.HIGH, "Acceso sospechoso", NOW);

        assertThatThrownBy(() -> anomaly.resolve(ADMIN_ID, "x".repeat(1001), NOW))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rehydratedResolvedAnomalyRequiresResolutionData() {
        assertThatThrownBy(() -> Anomaly.rehydrate(
                        UUID.randomUUID(),
                        AnomalyType.SUSPICIOUS_ACCESS,
                        AnomalySeverity.HIGH,
                        AnomalyStatus.RESOLVED,
                        "Acceso sospechoso",
                        NOW,
                        null,
                        ADMIN_ID,
                        "Nota"))
                .isInstanceOf(ValidationException.class);
    }
}
