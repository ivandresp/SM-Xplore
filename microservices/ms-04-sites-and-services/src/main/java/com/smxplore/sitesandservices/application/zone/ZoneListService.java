package com.smxplore.sitesandservices.application.zone;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.model.zone.ZoneFilter;
import com.smxplore.sitesandservices.domain.ports.in.ListZonesUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ZoneRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ZoneListService implements ListZonesUseCase {

    private final ZoneRepositoryPort zoneRepository;

    @Override
    public Mono<PageResult<Zone>> handle(ZoneFilter filter) {
        return zoneRepository.findAll(filter);
    }
}
