package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.service.Service;
import reactor.core.publisher.Mono;

public interface CreateServiceUseCase {

    Mono<Service> handle(Service service, Actor actor, String correlationId);
}
