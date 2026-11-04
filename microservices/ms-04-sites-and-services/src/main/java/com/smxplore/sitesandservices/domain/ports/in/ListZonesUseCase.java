package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.model.zone.ZoneFilter;
import reactor.core.publisher.Mono;

public interface ListZonesUseCase {

    Mono<PageResult<Zone>> handle(ZoneFilter filter);
}
