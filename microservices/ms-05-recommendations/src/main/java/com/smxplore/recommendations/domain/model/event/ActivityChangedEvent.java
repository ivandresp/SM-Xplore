package com.smxplore.recommendations.domain.model.event;

import com.smxplore.recommendations.domain.model.catalog.ContentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Activity event projected by ms-05.
 *
 * <p>v1 carries {@code categoryId} and coordinates. v2 carries the free-text {@code category}
 * and {@code siteId}; coordinates are resolved from the site when available. {@code categoryName}
 * is preserved for the recommendation reason and for linking categories by name.
 */
public record ActivityChangedEvent(
        EventMetadata metadata,
        UUID activityId,
        String name,
        UUID categoryId,
        String categoryName,
        UUID siteId,
        Double latitude,
        Double longitude,
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
        EventValidation.coordinates(latitude, longitude);
        EventValidation.nonNegative(price);
        EventValidation.required(status, "status");
        EventValidation.instant(updatedAt, "updatedAt");
        categoryName = categoryName == null || categoryName.isBlank() ? null : categoryName.trim();
    }

    /**
     * Compatibility constructor for v1 and existing tests: no category name and mandatory
     * coordinates from the event payload.
     */
    public ActivityChangedEvent(
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
    ) {
        this(metadata, activityId, name, categoryId, null, siteId,
                latitude, longitude, price, status, updatedAt);
    }
}
