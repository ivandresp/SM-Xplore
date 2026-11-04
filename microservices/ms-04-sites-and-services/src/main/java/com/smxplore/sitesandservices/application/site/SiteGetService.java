package com.smxplore.sitesandservices.application.site;

import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.ports.in.GetSiteUseCase;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class SiteGetService implements GetSiteUseCase {

    private final CatalogLookup lookup;

    @Override
    public Mono<Site> handle(UUID siteId) {
        return lookup.site(siteId);
    }
}
