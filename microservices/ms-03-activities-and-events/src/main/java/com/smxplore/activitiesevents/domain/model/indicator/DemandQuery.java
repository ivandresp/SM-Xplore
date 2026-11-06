package com.smxplore.activitiesevents.domain.model.indicator;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Internal projection query over reservation rows. {@code guideId} scopes a guide's activities;
 * {@code activityIds}/{@code eventIds} scope a manager's sites. A {@code null} collection means
 * "any item", while an empty collection means "none" and avoids querying the database.
 */
public record DemandQuery(
        Instant from,
        Instant to,
        UUID guideId,
        Collection<UUID> activityIds,
        Collection<UUID> eventIds
) {

    public DemandQuery {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Demand query from and to are required.");
        }
        activityIds = activityIds == null ? null : activityIds.stream().toList();
        eventIds = eventIds == null ? null : eventIds.stream().toList();
        if (activityIds != null && activityIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("Demand query activityIds cannot contain nulls.");
        }
        if (eventIds != null && eventIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("Demand query eventIds cannot contain nulls.");
        }
    }

    public boolean hasNoItems() {
        return guideId == null
                && activityIds != null && activityIds.isEmpty()
                && eventIds != null && eventIds.isEmpty();
    }
}
