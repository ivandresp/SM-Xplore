package com.smxplore.sitesandservices.application.zone;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalPgOutboxWriter;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.ports.in.ChangeZoneStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ZoneRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public class ZoneChangeStatusService implements ChangeZoneStatusUseCase {

    private final ZoneRepositoryPort zoneRepository;
    private final SiteRepositoryPort siteRepository;
    private final ServiceRepositoryPort serviceRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final TransactionalPgOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<Zone> handle(Zone zone, Actor actor, String correlationId) {
        return access.requireManager(actor)
                .then(lookup.zone(zone.zoneId()))
                .flatMap(existing -> access.requireZoneOwner(existing, actor))
                .flatMap(existing -> {
                    Zone updated = existing.toBuilder()
                            .status(zone.status())
                            .updatedAt(Instant.now(clock))
                            .build();
                    updated.validate();
                    return outboxWriter.save(
                            zoneRepository.save(updated),
                            eventFactory::zoneChanged,
                            correlationId
                    );
                })
                .flatMap(this::refreshSnapshots);
    }

    private Mono<Zone> refreshSnapshots(Zone zone) {
        return Mono.when(
                        siteRepository.updateZoneSnapshot(zone.zoneId(), zone.name(), zone.status()),
                        serviceRepository.updateZoneSnapshot(zone.zoneId(), zone.name(), zone.status())
                )
                .thenReturn(zone);
    }
}
