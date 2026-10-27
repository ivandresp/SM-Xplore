package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import reactor.core.publisher.Mono;

public interface CreateZoneUseCase {

    Mono<Zone> handle(Zone zone, Actor actor);
}
