package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.service.ServiceFilter;
import reactor.core.publisher.Mono;

public interface ListServicesUseCase {

    Mono<PageResult<Service>> handle(ServiceFilter filter);
}
