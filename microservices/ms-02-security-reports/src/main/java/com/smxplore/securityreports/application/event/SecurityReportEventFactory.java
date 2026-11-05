package com.smxplore.securityreports.application.event;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.event.DomainEvent;
import com.smxplore.securityreports.domain.model.types.Location;
import lombok.RequiredArgsConstructor;

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

    public DomainEvent reportChanged(SecurityReport report, String correlationId) {
        Location location = report.getLocation();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", report.getId().toString());
        payload.put("reporterId", report.getUser().id().toString());
        payload.put("title", report.getTitle());
        payload.put("status", report.getStatus().name());
        payload.put("latitude", location == null ? null : location.latitude());
        payload.put("longitude", location == null ? null : location.longitude());
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
}
