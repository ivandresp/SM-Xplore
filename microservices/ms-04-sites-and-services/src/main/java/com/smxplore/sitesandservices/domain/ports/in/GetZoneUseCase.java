package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.zone.Zone;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetZoneUseCase {

    Mono<Zone> handle(UUID zoneId);
}
