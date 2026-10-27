package com.smxplore.sitesandservices.application.zone;

import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.ports.in.UpdateZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ZoneRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public class ZoneUpdateService implements UpdateZoneUseCase {

    private final ZoneRepositoryPort zoneRepository;
    private final SiteRepositoryPort siteRepository;
    private final ServiceRepositoryPort serviceRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final Clock clock;

    @Override
    public Mono<Zone> handle(Zone zone, Actor actor) {
        return access.requireManager(actor)
                .then(lookup.zone(zone.zoneId()))
                .flatMap(existing -> access.requireZoneOwner(existing, actor))
                .flatMap(existing -> {
                    Zone updated = existing.toBuilder()
                            .name(zone.name())
                            .type(zone.type())
                            .description(zone.description())
                            .updatedAt(Instant.now(clock))
                            .build();
                    updated.validate();
                    return zoneRepository.save(updated);
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
