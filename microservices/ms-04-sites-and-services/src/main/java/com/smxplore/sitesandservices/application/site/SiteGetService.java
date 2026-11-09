package com.smxplore.sitesandservices.application.site;

import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.ports.in.GetSiteUseCase;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class SiteGetService implements GetSiteUseCase {

    private final CatalogLookup lookup;
    private final CatalogAccess access;

    @Override
    public Mono<Site> handle(UUID siteId, Actor actor) {
        return lookup.site(siteId).flatMap(site -> access.requireSiteVisible(site, actor));
    }
}
