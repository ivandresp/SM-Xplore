package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.site.Site;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetSiteUseCase {

    Mono<Site> handle(UUID siteId);
}
