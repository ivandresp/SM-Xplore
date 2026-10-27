package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.model.zone.ZoneFilter;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ZoneRepositoryPort {

    Mono<Zone> save(Zone zone);

    Mono<Zone> findById(UUID zoneId);

    Mono<PageResult<Zone>> findAll(ZoneFilter filter);
}
