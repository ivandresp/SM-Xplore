package com.smxplore.sitesandservices.application.service;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.service.ServiceFilter;
import com.smxplore.sitesandservices.domain.ports.in.ListServicesUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ServiceListService implements ListServicesUseCase {

    private final ServiceRepositoryPort serviceRepository;

    @Override
    public Mono<PageResult<Service>> handle(ServiceFilter filter) {
        return serviceRepository.findAll(filter);
    }
}
