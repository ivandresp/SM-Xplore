package com.smxplore.sitesandservices.application.site;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.site.SiteFilter;
import com.smxplore.sitesandservices.domain.ports.in.ListSitesUseCase;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class SiteListService implements ListSitesUseCase {

    private final SiteRepositoryPort siteRepository;

    @Override
    public Mono<PageResult<Site>> handle(SiteFilter filter) {
        return siteRepository.findAll(filter);
    }
}
