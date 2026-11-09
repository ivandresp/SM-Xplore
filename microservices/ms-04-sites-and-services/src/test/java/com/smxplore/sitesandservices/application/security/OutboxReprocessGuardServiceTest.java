package com.smxplore.sitesandservices.application.security;

import com.smxplore.sitesandservices.application.support.InMemoryRateLimiter;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.ports.out.SecurityAuditRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.adapters.out.outbox.OutboxReprocessService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * MS04-10: el reproceso de outbox es solo para ADMIN, con un limite acotado (1..500) y limitado
 * por cuenta antes de tocar la base de datos.
 */
class OutboxReprocessGuardServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final DatabaseClient databaseClient = mock(DatabaseClient.class);
    private final ReactiveMongoTemplate mongoTemplate = mock(ReactiveMongoTemplate.class);
    private final SecurityAuditRepositoryPort auditRepository = mock(SecurityAuditRepositoryPort.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final OutboxReprocessService service = new OutboxReprocessService(
            databaseClient, mongoTemplate, auditRepository, meterRegistry, CLOCK);

    private final Actor admin = new Actor(UUID.randomUUID(), UserRole.ADMIN, "Admin");

    @Test
    void nonAdminsCannotReprocess() {
        StepVerifier.create(service.reprocessFailed(10, false, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class)
                        .hasMessageContaining("Only an administrator"))
                .verify();
        StepVerifier.create(service.reprocessFailed(10, false, new Actor(
                        UUID.randomUUID(), UserRole.DESTINATION_MANAGER, "Gestora")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class))
                .verify();

        verifyNoInteractions(databaseClient, mongoTemplate, auditRepository);
    }

    @Test
    void limitsMustBeBetweenOneAndFiveHundred() {
        StepVerifier.create(service.reprocessFailed(0, false, admin))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidCatalogException.class)
                        .hasMessageContaining("limit must be between 1 and 500"))
                .verify();
        StepVerifier.create(service.reprocessFailed(501, false, admin))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidCatalogException.class))
                .verify();

        verifyNoInteractions(databaseClient, mongoTemplate, auditRepository);
    }
}
