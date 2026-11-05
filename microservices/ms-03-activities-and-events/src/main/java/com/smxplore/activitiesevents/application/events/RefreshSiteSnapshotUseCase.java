package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies a {@code catalog.site.changed} v2 to the snapshot ms-03 keeps on its events: the site
 * name and the manager that owns it.
 *
 * <p>The update only wins when the site event is newer than the last snapshot applied, so a
 * reordered message cannot hand the site back to a previous manager.
 */
@RequiredArgsConstructor
public class RefreshSiteSnapshotUseCase {

    private final EventRepositoryPort eventRepo;

    public Mono<Integer> handle(SiteChanged change) {
        return eventRepo.applySiteSnapshot(change.siteId(), change.siteName(), change.managerId(),
                change.updatedAt());
    }

    /** The part of {@code catalog.site.changed} v2 that ms-03 stores. */
    public record SiteChanged(UUID siteId, String siteName, UUID managerId, Instant updatedAt) {

        public SiteChanged {
            if (siteId == null) {
                throw new InvalidEventRequestException("Site id is mandatory.");
            }
            if (managerId == null) {
                throw new InvalidEventRequestException("Site manager is mandatory.");
            }
            if (updatedAt == null) {
                throw new InvalidEventRequestException("Site update instant is mandatory.");
            }
        }
    }
}
