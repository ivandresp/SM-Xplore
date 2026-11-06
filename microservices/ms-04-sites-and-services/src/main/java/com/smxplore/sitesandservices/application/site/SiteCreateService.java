package com.smxplore.sitesandservices.application.site;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.ports.in.CreateSiteUseCase;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class SiteCreateService implements CreateSiteUseCase {

    private final SiteRepositoryPort siteRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final TransactionalOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<Site> handle(Site site, Actor actor, String correlationId) {
        return access.requireManager(actor)
                .flatMap(manager -> lookup.siteContext(site.categoryId(), site.zoneId())
                        .flatMap(context -> {
                            Instant now = Instant.now(clock);
                            Site created = site.toBuilder()
                                    .siteId(UUID.randomUUID())
                                    .managerId(manager.userId())
                                    .categoryName(context.category().name())
                                    .zoneName(context.zone().name())
                                    .zoneStatus(context.zone().status())
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .build();
                            created.validate();
                            return outboxWriter.save(
                                    siteRepository.save(created),
                                    eventFactory::siteChanged,
                                    correlationId
                            );
                        }));
    }
}
