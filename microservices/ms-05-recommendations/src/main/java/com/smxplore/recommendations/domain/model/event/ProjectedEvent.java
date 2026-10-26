package com.smxplore.recommendations.domain.model.event;

import java.time.Instant;

/**
 * A contract event that ms-05 can project into Neo4j.
 */
public sealed interface ProjectedEvent permits
        CategoryChangedEvent,
        SiteChangedEvent,
        ServiceChangedEvent,
        ActivityChangedEvent,
        EventItemChangedEvent,
        ReviewChangedEvent {

    EventMetadata metadata();

    Instant updatedAt();

    default String eventType() {
        return metadata().eventType();
    }

    default String aggregateId() {
        return metadata().aggregateId().toString();
    }
}
