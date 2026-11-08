package com.smxplore.sitesandservices.application.site;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.ports.in.UpdateSiteUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public class SiteUpdateService implements UpdateSiteUseCase {

    private final SiteRepositoryPort siteRepository;
    private final ServiceRepositoryPort serviceRepository;
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
                        .flatMap(context -> access.requireZoneAssignment(context.zone(), actor)
                                .flatMap(zone -> {
                                    Site updated = existing.toBuilder()
                                            .name(site.name())
                                            .description(site.description())
                                            .categoryId(context.category().categoryId())
                                            .categoryName(context.category().name())
                                            .zoneId(zone.zoneId())
                                            .zoneName(zone.name())
                                            .zoneStatus(zone.status())
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
                                    ).flatMap(saved -> refreshServiceSnapshots(existing, saved));
                                })));
    }

    /**
     * Los servicios guardan el nombre del sitio como snapshot. Si el sitio se renombra,
     * hay que refrescar ese snapshot para que catálogo, reseñas y reservas no muestren
     * el nombre anterior.
     */
    private Mono<Site> refreshServiceSnapshots(Site previous, Site saved) {
        if (previous.name().equals(saved.name())) {
            return Mono.just(saved);
        }
        return serviceRepository.updateSiteSnapshot(saved.siteId(), saved.name()).thenReturn(saved);
    }
}
