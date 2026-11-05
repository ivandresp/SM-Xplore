package com.smxplore.recommendations.domain.model.event;

import com.smxplore.recommendations.domain.model.catalog.ContentStatus;

import java.time.Instant;
import java.util.UUID;

public record ActivityChangedEvent(
        EventMetadata metadata,
        UUID activityId,
        String name,
        UUID categoryId,
        UUID siteId,
        double latitude,
        double longitude,
        double price,
        ContentStatus status,
        Instant updatedAt
) implements ProjectedEvent {

    public ActivityChangedEvent {
        EventValidation.metadata(metadata);
        EventValidation.uuid(activityId, "activityId");
        EventValidation.text(name, "activity name");
        EventValidation.uuid(categoryId, "categoryId");
        EventValidation.uuid(siteId, "siteId");
        EventValidation.latitude(latitude);
        EventValidation.longitude(longitude);
        EventValidation.nonNegative(price);
        EventValidation.required(status, "status");
        EventValidation.instant(updatedAt, "updatedAt");
    }
}
