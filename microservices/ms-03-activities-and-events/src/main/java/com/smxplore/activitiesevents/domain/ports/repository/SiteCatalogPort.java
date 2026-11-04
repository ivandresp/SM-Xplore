package com.smxplore.activitiesevents.domain.ports.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Read-only view of the ms-04 site catalog. ms-03 never owns site data; it copies
 * a snapshot for event authorization.
 */
public interface SiteCatalogPort {

    Mono<SiteSnapshot> findById(UUID siteId, String bearerToken);

    /** All sites managed by {@code managerId}, following the ms-04 pages. */
    Flux<SiteSnapshot> findByManagerId(UUID managerId, String bearerToken);

    record SiteSnapshot(UUID siteId, UUID managerId, String name) {
    }
}
