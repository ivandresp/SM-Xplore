package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.site.SiteFilter;
import reactor.core.publisher.Mono;

public interface ListSitesUseCase {

    Mono<PageResult<Site>> handle(SiteFilter filter);
}
