package com.smxplore.recommendations.domain.model.event;

import com.smxplore.recommendations.domain.model.catalog.ItemType;

import java.time.Instant;
import java.util.UUID;

public record ReviewChangedEvent(
        EventMetadata metadata,
        UUID reviewId,
        UUID touristId,
        ItemType itemType,
        UUID itemId,
        int rating,
        ReviewStatus status,
        Instant updatedAt
) implements ProjectedEvent {

    public ReviewChangedEvent {
        EventValidation.metadata(metadata);
        EventValidation.uuid(reviewId, "reviewId");
        EventValidation.uuid(touristId, "touristId");
        EventValidation.required(itemType, "itemType");
        EventValidation.uuid(itemId, "itemId");
        EventValidation.rating(rating);
        EventValidation.required(status, "status");
        EventValidation.instant(updatedAt, "updatedAt");
    }
}
