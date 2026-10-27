package com.smxplore.sitesandservices.application.site;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.ports.in.AssignSiteZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public class SiteAssignZoneService implements AssignSiteZoneUseCase {

    private final SiteRepositoryPort siteRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final TransactionalOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<Site> handle(Site site, Actor actor, String correlationId) {
        return lookup.site(site.siteId())
                .flatMap(existing -> access.requireSiteOwner(existing, actor))
                .flatMap(existing -> lookup.zone(site.zoneId())
                        .flatMap(zone -> {
                            Site updated = existing.toBuilder()
                                    .zoneId(zone.zoneId())
                                    .zoneName(zone.name())
                                    .zoneStatus(zone.status())
                                    .updatedAt(Instant.now(clock))
                                    .build();
                            updated.validate();
                            return outboxWriter.save(
                                    siteRepository.save(updated),
                                    eventFactory::siteChanged,
                                    correlationId
                            );
                        }));
    }
}
