package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.site.SiteFilter;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SiteRepositoryPort {

    Mono<Site> save(Site site);

    Mono<Site> findById(UUID siteId);

    Mono<PageResult<Site>> findAll(SiteFilter filter);

    Mono<Void> updateZoneSnapshot(UUID zoneId, String zoneName, ZoneStatus zoneStatus);

    Mono<Void> updateCategorySnapshot(UUID categoryId, String categoryName);

    Mono<Boolean> existsById(UUID siteId);
}
