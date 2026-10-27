package com.smxplore.sitesandservices.application.site;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.ports.in.UpdateSiteUseCase;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public class SiteUpdateService implements UpdateSiteUseCase {

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
                .flatMap(existing -> lookup.siteContext(site.categoryId(), site.zoneId())
                        .flatMap(context -> {
                            Site updated = existing.toBuilder()
                                    .name(site.name())
                                    .description(site.description())
                                    .categoryId(context.category().categoryId())
                                    .categoryName(context.category().name())
                                    .zoneId(context.zone().zoneId())
                                    .zoneName(context.zone().name())
                                    .zoneStatus(context.zone().status())
                                    .latitude(site.latitude())
                                    .longitude(site.longitude())
                                    .photosUrls(site.photosUrls())
                                    .maxCapacity(site.maxCapacity())
                                    .availability(site.availability())
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
