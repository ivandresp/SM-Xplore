package com.smxplore.sitesandservices.application.service;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.ports.in.CreateServiceUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ServiceCreateService implements CreateServiceUseCase {

    private final ServiceRepositoryPort serviceRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final TransactionalOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<Service> handle(Service service, Actor actor, String correlationId) {
        return access.requireProvider(actor)
                .flatMap(provider -> lookup.serviceContext(
                                service.siteId(), service.categoryId(), service.zoneId())
                        .flatMap(context -> {
                            Instant now = Instant.now(clock);
                            Service created = service.toBuilder()
                                    .serviceId(UUID.randomUUID())
                                    .providerId(provider.userId())
                                    .providerName(provider.displayName())
                                    .siteName(context.site().name())
                                    .zoneName(context.zone().name())
                                    .zoneStatus(context.zone().status())
                                    .categoryName(context.category().name())
                                    .price(normalizePrice(service.price()))
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .build();
                            created.validate();
                            return outboxWriter.save(
                                    serviceRepository.save(created),
                                    eventFactory::serviceChanged,
                                    correlationId
                            );
                        }));
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        return price == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : price.setScale(2, RoundingMode.HALF_UP);
    }
}
