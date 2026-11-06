package com.smxplore.recommendations.domain.model.event;

import com.smxplore.recommendations.domain.model.catalog.ContentStatus;

import java.time.Instant;
import java.util.UUID;

public record SiteChangedEvent(
        EventMetadata metadata,
        UUID siteId,
        String name,
        UUID categoryId,
        UUID zoneId,
        double latitude,
        double longitude,
        ContentStatus status,
        Instant updatedAt
) implements ProjectedEvent {

    public SiteChangedEvent {
        EventValidation.metadata(metadata);
        EventValidation.uuid(siteId, "siteId");
        EventValidation.text(name, "site name");
        EventValidation.uuid(categoryId, "categoryId");
        EventValidation.latitude(latitude);
        EventValidation.longitude(longitude);
        EventValidation.required(status, "status");
        EventValidation.instant(updatedAt, "updatedAt");
    }

    /**
     * Compatibility constructor for payloads without a zone (v1, or v2 before ms-04 emits
     * {@code zoneId}).
     */
    public SiteChangedEvent(
            EventMetadata metadata,
            UUID siteId,
            String name,
            UUID categoryId,
            double latitude,
            double longitude,
            ContentStatus status,
            Instant updatedAt
    ) {
        this(metadata, siteId, name, categoryId, null, latitude, longitude, status, updatedAt);
    }
}
