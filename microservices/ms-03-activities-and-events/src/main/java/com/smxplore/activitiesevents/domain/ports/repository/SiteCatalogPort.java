package com.smxplore.activitiesevents.domain.ports.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Read-only view of the ms-04 site catalog. ms-03 never owns site data; it copies a snapshot for
 * event authorization and uses it to group demand indicators by site and zone.
 */
public interface SiteCatalogPort {

    Mono<SiteSnapshot> findById(UUID siteId, String bearerToken);

    /** All sites managed by {@code managerId}, following the ms-04 pages. */
    Flux<SiteSnapshot> findByManagerId(UUID managerId, String bearerToken);

    /** All sites of a zone, following the ms-04 pages. */
    Flux<SiteSnapshot> findByZoneId(UUID zoneId, String bearerToken);

    record SiteSnapshot(
            UUID siteId,
            UUID managerId,
            String name,
            UUID zoneId,
            String zoneName,
            String zoneStatus
    ) {

        /** Keeps existing callers that only need the identity and name. */
        public SiteSnapshot(UUID siteId, UUID managerId, String name) {
            this(siteId, managerId, name, null, null, null);
        }
    }
}
