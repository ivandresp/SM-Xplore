package com.smxplore.users.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.identity.application.dto.AuditEntryView;
import com.smxplore.users.identity.application.dto.AuditSearchCriteria;
import com.smxplore.users.identity.application.dto.PageResult;
import com.smxplore.users.identity.application.port.out.AuditLogRepository;
import com.smxplore.users.identity.domain.model.AuditAction;
import com.smxplore.users.identity.domain.model.AuditEntry;
import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ListAuditEntriesServiceTest {

    @Test
    void mapsEntriesAndPaginationInformation() {
        UserId actorId = UserId.newId();
        AuditEntry entry = AuditEntry.create(
                actorId,
                "admin@test.local",
                Role.ADMIN,
                AuditAction.ESTADO_CAMBIADO,
                UserId.newId(),
                Map.of("estadoNuevo", "BLOQUEADO"),
                Instant.parse("2026-01-01T10:00:00Z"));
        AuditSearchCriteria criteria = new AuditSearchCriteria(null, null, null, null, null, 1, 20);
        AuditLogRepository repository = new AuditLogRepository() {
            @Override
            public AuditEntry save(AuditEntry candidate) {
                return candidate;
            }

            @Override
            public PageResult<AuditEntry> search(AuditSearchCriteria candidate) {
                assertThat(candidate).isEqualTo(criteria);
                return PageResult.of(List.of(entry), 1, 20, 21);
            }
        };

        PageResult<AuditEntryView> result = new ListAuditEntriesService(repository).list(criteria);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.content()).singleElement().satisfies(view -> {
            assertThat(view.action()).isEqualTo(AuditAction.ESTADO_CAMBIADO);
            assertThat(view.actorId()).isEqualTo(actorId.value());
            assertThat(view.details()).containsEntry("estadoNuevo", "BLOQUEADO");
        });
    }
}
