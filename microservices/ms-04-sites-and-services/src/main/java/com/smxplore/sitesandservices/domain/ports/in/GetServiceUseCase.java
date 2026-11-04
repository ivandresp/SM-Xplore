package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.service.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetServiceUseCase {

    Mono<Service> handle(UUID serviceId);
}
