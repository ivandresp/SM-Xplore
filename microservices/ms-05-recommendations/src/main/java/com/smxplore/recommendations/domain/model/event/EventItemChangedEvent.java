package com.smxplore.recommendations.domain.model.event;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.model.catalog.ContentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event for an event item published by ms-03.
 * Its payload field is called {@code eventId}; internally it is the catalog item id.
 */
public record EventItemChangedEvent(
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
) implements ProjectedEvent {

    public EventItemChangedEvent {
        EventValidation.metadata(metadata);
        EventValidation.uuid(itemId, "eventId");
        EventValidation.text(name, "event name");
        EventValidation.uuid(categoryId, "categoryId");
        EventValidation.uuid(siteId, "siteId");
        EventValidation.latitude(latitude);
        EventValidation.longitude(longitude);
        EventValidation.nonNegative(price);
        EventValidation.instant(startsAt, "startsAt");
        EventValidation.required(status, "status");
        EventValidation.instant(updatedAt, "updatedAt");
        if (endsAt != null && endsAt.isBefore(startsAt)) {
            throw new InvalidEventException("endsAt must be after startsAt");
        }
    }
}
