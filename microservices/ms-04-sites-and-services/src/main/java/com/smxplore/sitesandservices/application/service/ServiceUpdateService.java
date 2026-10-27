package com.smxplore.sitesandservices.application.service;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.ports.in.UpdateServiceUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public class ServiceUpdateService implements UpdateServiceUseCase {

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
                .flatMap(existing -> lookup.serviceContext(
                                service.siteId(), service.categoryId(), service.zoneId())
                        .flatMap(context -> {
                            Service updated = existing.toBuilder()
                                    .siteId(context.site().siteId())
                                    .siteName(context.site().name())
                                    .zoneId(context.zone().zoneId())
                                    .zoneName(context.zone().name())
                                    .zoneStatus(context.zone().status())
                                    .categoryId(context.category().categoryId())
                                    .categoryName(context.category().name())
                                    .name(service.name())
                                    .description(service.description())
                                    .type(service.type())
                                    .latitude(service.latitude())
                                    .longitude(service.longitude())
                                    .price(normalizePrice(service.price()))
                                    .maxCapacity(service.maxCapacity())
                                    .availability(service.availability())
                                    .photosUrls(service.photosUrls())
                                    .cancellationPolicy(service.cancellationPolicy())
                                    .updatedAt(Instant.now(clock))
                                    .build();
                            updated.validate();
                            return outboxWriter.save(
                                    serviceRepository.save(updated),
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
