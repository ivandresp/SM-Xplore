package com.smxplore.securityreports.application.event;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.event.DomainEvent;
import com.smxplore.securityreports.domain.model.types.Location;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class SecurityReportEventFactory {

    public static final String EVENT_TYPE = "security.report.changed";

    private final String producer;
    private final Clock clock;

    /**
     * MS02-09: el sobre v1 exige {@code reporterId} y {@code title}, asi que no se pueden quitar sin romper el
     * contrato; lo que si se minimiza es la precision de la ubicacion (3 decimales, ~110 m) y la retencion del
     * outbox. El resto de PII queda documentado como riesgo aceptado hasta que P3-9 revise el contrato.
     */
    public DomainEvent reportChanged(SecurityReport report, String correlationId) {
        Location location = report.getLocation();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", report.getId().toString());
        payload.put("reporterId", report.getUser().id().toString());
        payload.put("title", report.getTitle());
        payload.put("status", report.getStatus().name());
        payload.put("latitude", location == null ? null : roundCoordinate(location.latitude()));
        payload.put("longitude", location == null ? null : roundCoordinate(location.longitude()));
        payload.put("createdAt", report.getCreatedAt().toString());
        payload.put("updatedAt", report.getUpdatedAt().toString());

        return new DomainEvent(
                UUID.randomUUID(),
                EVENT_TYPE,
                1,
                Instant.now(clock),
                report.getId(),
                correlationId,
                producer,
                payload
        );
    }

    private static Double roundCoordinate(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
