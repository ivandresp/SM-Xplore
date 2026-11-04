package com.smxplore.sitesandservices.application.zone;

import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.ports.in.GetZoneUseCase;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ZoneGetService implements GetZoneUseCase {

    private final CatalogLookup lookup;

    @Override
    public Mono<Zone> handle(UUID zoneId) {
        return lookup.zone(zoneId);
    }
}
