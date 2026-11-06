package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.indicator.DemandFilter;
import com.smxplore.sitesandservices.domain.model.indicator.DemandIndicators;
import reactor.core.publisher.Mono;

public interface GetDemandIndicatorsUseCase {

    Mono<DemandIndicators> handle(DemandFilter filter, Actor actor);
}
