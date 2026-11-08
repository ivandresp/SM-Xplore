package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MS02-15: el registro de auditoria de una transicion exige actor, estados y timestamp.
 */
class StatusChangeTests {

    private static final UUID ACTOR = UUID.fromString("66666666-6666-6666-6666-666666666601");
    private static final Instant AT = Instant.parse("2026-11-08T12:00:00Z");

    @Test
    void keepsTheAuditFields() {
        var change = new StatusChange(ACTOR, SecurityReportStatus.ONGOING, SecurityReportStatus.REPORTED, AT, "corr-1");

        assertThat(change.actorId()).isEqualTo(ACTOR);
        assertThat(change.from()).isEqualTo(SecurityReportStatus.ONGOING);
        assertThat(change.to()).isEqualTo(SecurityReportStatus.REPORTED);
        assertThat(change.at()).isEqualTo(AT);
        assertThat(change.correlationId()).isEqualTo("corr-1");
    }

    @Test
    void requiresAnActor() {
        assertThatThrownBy(() -> new StatusChange(null, SecurityReportStatus.ONGOING,
                SecurityReportStatus.REPORTED, AT, "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actor");
    }

    @Test
    void requiresOriginAndTargetStatuses() {
        assertThatThrownBy(() -> new StatusChange(ACTOR, null, SecurityReportStatus.REPORTED, AT, "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origin and target");
        assertThatThrownBy(() -> new StatusChange(ACTOR, SecurityReportStatus.ONGOING, null, AT, "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origin and target");
    }

    @Test
    void requiresADifferentTargetStatus() {
        assertThatThrownBy(() -> new StatusChange(ACTOR, SecurityReportStatus.ONGOING,
                SecurityReportStatus.ONGOING, AT, "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different target");
    }

    @Test
    void requiresATimestamp() {
        assertThatThrownBy(() -> new StatusChange(ACTOR, SecurityReportStatus.ONGOING,
                SecurityReportStatus.REPORTED, null, "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");
    }
}
