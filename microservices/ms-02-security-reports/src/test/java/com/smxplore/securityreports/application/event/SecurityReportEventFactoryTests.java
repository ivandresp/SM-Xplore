package com.smxplore.securityreports.application.event;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.Location;
import com.smxplore.securityreports.domain.model.types.UserRef;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS02-09: el contrato v1 conserva los campos, pero la ubicacion publicada se reduce a 3 decimales.
 */
class SecurityReportEventFactoryTests {

    private static final Instant NOW = Instant.parse("2026-11-04T17:00:00Z");

    private final SecurityReportEventFactory factory =
            new SecurityReportEventFactory("ms-02", Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void roundsThePublishedCoordinatesToThreeDecimals() {
        var report = reportAt(10.3916499, -75.4786388);

        var event = factory.reportChanged(report, "correlation-id");

        assertThat(event.payload().get("latitude")).isEqualTo(10.392);
        assertThat(event.payload().get("longitude")).isEqualTo(-75.479);
    }

    @Test
    void keepsTheFieldsRequiredByTheV1Contract() {
        var event = factory.reportChanged(reportAt(10.3916, -75.4786), "correlation-id");

        assertThat(event.eventType()).isEqualTo("security.report.changed");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.producer()).isEqualTo("ms-02");
        assertThat(event.payload()).containsOnlyKeys(
                "reportId", "reporterId", "title", "status", "latitude", "longitude", "createdAt", "updatedAt");
    }

    @Test
    void keepsNullCoordinatesWhenTheReportHasNoLocation() {
        var report = SecurityReport.builder()
                .id(UUID.randomUUID())
                .user(new UserRef(UUID.randomUUID(), "Nestor"))
                .title("Reporte sin ubicacion")
                .build();

        var event = factory.reportChanged(report, "correlation-id");

        assertThat(event.payload().get("latitude")).isNull();
        assertThat(event.payload().get("longitude")).isNull();
    }

    private static SecurityReport reportAt(double latitude, double longitude) {
        return SecurityReport.builder()
                .id(UUID.randomUUID())
                .user(new UserRef(UUID.randomUUID(), "Nestor"))
                .title("Robo en la playa")
                .location(new Location(latitude, longitude))
                .build();
    }
}
