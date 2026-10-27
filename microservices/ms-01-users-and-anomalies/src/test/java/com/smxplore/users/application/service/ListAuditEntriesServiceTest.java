package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.AuditEntryView;
import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.port.out.AuditLogRepository;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.AuditEntry;
import com.smxplore.users.domain.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ListAuditEntriesServiceTest {

    @Test
    void mapsEntriesAndPaginationInformation() {
        UUID actorId = UUID.randomUUID();
        AuditEntry entry = AuditEntry.create(
                actorId,
                "admin@test.local",
                Role.ADMIN,
                AuditAction.ESTADO_CAMBIADO,
                UUID.randomUUID(),
                Map.of("estadoNuevo", "BLOQUEADO"),
                Instant.parse("2026-01-01T10:00:00Z"));
        AuditSearchCriteria criteria = new AuditSearchCriteria(null, null, null, null, null, 1, 20);
        AuditLogRepository repository = new AuditLogRepository() {
            @Override
            public Mono<AuditEntry> save(AuditEntry candidate) {
                return Mono.just(candidate);
            }

            @Override
            public Mono<PageResult<AuditEntry>> search(AuditSearchCriteria candidate) {
                assertThat(candidate).isEqualTo(criteria);
                return Mono.just(PageResult.of(List.of(entry), 1, 20, 21));
            }

            @Override
            public Flux<AuditEntry> export(AuditSearchCriteria candidate, int limit) {
                return Flux.empty();
            }
        };

        PageResult<AuditEntryView> result =
                new ListAuditEntriesService(repository).list(criteria).block();

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.content()).singleElement().satisfies(view -> {
            assertThat(view.action()).isEqualTo(AuditAction.ESTADO_CAMBIADO);
            assertThat(view.actorId()).isEqualTo(actorId);
            assertThat(view.details()).containsEntry("estadoNuevo", "BLOQUEADO");
        });
    }
}
