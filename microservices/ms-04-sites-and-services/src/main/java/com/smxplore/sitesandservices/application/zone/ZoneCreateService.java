package com.smxplore.sitesandservices.application.zone;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.TransactionalPgOutboxWriter;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.ports.in.CreateZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.out.ZoneRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class ZoneCreateService implements CreateZoneUseCase {

    private final ZoneRepositoryPort zoneRepository;
    private final CatalogAccess access;
    private final TransactionalPgOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<Zone> handle(Zone zone, Actor actor, String correlationId) {
        return access.requireManager(actor)
                .flatMap(manager -> {
                    Instant now = Instant.now(clock);
                    Zone created = zone.toBuilder()
                            .zoneId(UUID.randomUUID())
                            .managerId(manager.userId())
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    created.validate();
                    return outboxWriter.save(
                            zoneRepository.save(created),
                            eventFactory::zoneChanged,
                            correlationId
                    );
                });
    }
}
