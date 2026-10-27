package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.site.Site;
import reactor.core.publisher.Mono;

public interface AssignSiteZoneUseCase {

    Mono<Site> handle(Site site, Actor actor, String correlationId);
}
