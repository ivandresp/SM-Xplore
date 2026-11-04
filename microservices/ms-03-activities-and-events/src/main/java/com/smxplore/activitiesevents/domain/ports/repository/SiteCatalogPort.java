package com.smxplore.activitiesevents.domain.ports.repository;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Read-only view of the ms-04 site catalog. ms-03 never owns site data; it copies
 * a snapshot for event authorization.
 */
public interface SiteCatalogPort {

    Mono<SiteSnapshot> findById(UUID siteId);

    record SiteSnapshot(UUID siteId, UUID managerId, String name) {
    }
}
