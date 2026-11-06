package com.smxplore.recommendations.domain.model.event;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.model.catalog.ContentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event for an event item published by ms-03.
 * Its payload field is called {@code eventId}; internally it is the catalog item id.
 *
 * <p>v1 carries {@code categoryId} and coordinates. v2 carries the free-text {@code category}
 * and {@code siteId}; coordinates are resolved from the site when available.
 */
public record EventItemChangedEvent(
        EventMetadata metadata,
        UUID itemId,
        String name,
        UUID categoryId,
        String categoryName,
        UUID siteId,
        Double latitude,
        Double longitude,
        double price,
        Instant startsAt,
        Instant endsAt,
        ContentStatus status,
        Instant updatedAt
) implements ProjectedEvent {

    public EventItemChangedEvent {
        EventValidation.metadata(metadata);
        EventValidation.uuid(itemId, "eventId");
        EventValidation.text(name, "event name");
        EventValidation.uuid(categoryId, "categoryId");
        EventValidation.uuid(siteId, "siteId");
        EventValidation.coordinates(latitude, longitude);
        EventValidation.nonNegative(price);
        EventValidation.instant(startsAt, "startsAt");
        EventValidation.required(status, "status");
        EventValidation.instant(updatedAt, "updatedAt");
        categoryName = categoryName == null || categoryName.isBlank() ? null : categoryName.trim();
        if (endsAt != null && endsAt.isBefore(startsAt)) {
            throw new InvalidEventException("endsAt must be after startsAt");
        }
    }

    /**
     * Compatibility constructor for v1 and existing tests: no category name and mandatory
     * coordinates from the event payload.
     */
    public EventItemChangedEvent(
            EventMetadata metadata,
            UUID itemId,
            String name,
            UUID categoryId,
            UUID siteId,
            double latitude,
            double longitude,
            double price,
            Instant startsAt,
            Instant endsAt,
            ContentStatus status,
            Instant updatedAt
    ) {
        this(metadata, itemId, name, categoryId, null, siteId,
                latitude, longitude, price, startsAt, endsAt, status, updatedAt);
    }
}
