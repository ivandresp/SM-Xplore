package com.smxplore.sitesandservices.application.service;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.ports.in.ChangeServiceStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public class ServiceChangeStatusService implements ChangeServiceStatusUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final TransactionalOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<Service> handle(Service service, Actor actor, String correlationId) {
        return lookup.service(service.serviceId())
                .flatMap(existing -> access.requireServiceOwner(existing, actor))
                .flatMap(existing -> {
                    Service updated = existing.toBuilder()
                            .status(service.status())
                            .updatedAt(Instant.now(clock))
                            .build();
                    updated.validate();
                    return outboxWriter.save(
                            serviceRepository.save(updated),
                            eventFactory::serviceChanged,
                            correlationId
                    );
                });
    }
}
