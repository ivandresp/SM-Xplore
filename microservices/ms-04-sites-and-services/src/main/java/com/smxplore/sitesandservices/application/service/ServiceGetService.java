package com.smxplore.sitesandservices.application.service;

import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.ports.in.GetServiceUseCase;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ServiceGetService implements GetServiceUseCase {

    private final CatalogLookup lookup;
    private final CatalogAccess access;

    @Override
    public Mono<Service> handle(UUID serviceId, Actor actor) {
        return lookup.service(serviceId).flatMap(service -> access.requireServiceVisible(service, actor));
    }
}
