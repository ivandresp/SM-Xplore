package com.smxplore.recommendations.domain.model.event;

import com.smxplore.recommendations.domain.model.catalog.ContentStatus;

import java.time.Instant;
import java.util.UUID;

public record ServiceChangedEvent(
        EventMetadata metadata,
        UUID serviceId,
        UUID siteId,
        String name,
        String type,
        UUID categoryId,
        double latitude,
        double longitude,
        ContentStatus status,
        Instant updatedAt
) implements ProjectedEvent {

    public ServiceChangedEvent {
        EventValidation.metadata(metadata);
        EventValidation.uuid(serviceId, "serviceId");
        EventValidation.uuid(siteId, "siteId");
        EventValidation.text(name, "service name");
        EventValidation.text(type, "service type");
        EventValidation.uuid(categoryId, "categoryId");
        EventValidation.latitude(latitude);
        EventValidation.longitude(longitude);
        EventValidation.required(status, "status");
        EventValidation.instant(updatedAt, "updatedAt");
    }
}
