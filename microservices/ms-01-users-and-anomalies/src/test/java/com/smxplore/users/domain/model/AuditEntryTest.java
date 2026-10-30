package com.smxplore.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEntryTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void requiresActorEmail() {
        assertThatThrownBy(() -> AuditEntry.create(
                        UUID.randomUUID(), " ", Role.ADMIN, AuditAction.ESTADO_CAMBIADO, null, Map.of(), NOW))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void keepsAnImmutableCopyOfTheDetails() {
        Map<String, String> details = new HashMap<>();
        details.put("estadoNuevo", "BLOQUEADO");

        AuditEntry entry = AuditEntry.create(
                UUID.randomUUID(), "admin@test.local", Role.ADMIN, AuditAction.ESTADO_CAMBIADO, null, details, NOW);
        details.put("estadoNuevo", "ACTIVO");

        assertThat(entry.details()).containsEntry("estadoNuevo", "BLOQUEADO");
        assertThatThrownBy(() -> entry.details().put("otro", "valor"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void allowsAuditingActionsWithoutATargetUser() {
        AuditEntry entry = AuditEntry.create(
                UUID.randomUUID(), "admin@test.local", Role.ADMIN, AuditAction.ESTADO_CAMBIADO, null, null, NOW);

        assertThat(entry.targetUserId()).isNull();
        assertThat(entry.details()).isEmpty();
    }
}
