package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.AuditEntry;
import com.smxplore.users.domain.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditCsvFormatterTest {

    @Test
    void formatsHeaderAndEscapesCommasAndQuotes() {
        AuditEntry entry = AuditEntry.create(
                UUID.randomUUID(),
                "admin@test.local",
                Role.ADMIN,
                AuditAction.ANOMALIA_RESUELTA,
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                Map.of("motivo", "Texto, con \"comillas\""),
                Instant.parse("2026-10-27T10:00:00Z"));

        String csv = AuditCsvFormatter.format(List.of(entry));

        assertThat(csv).startsWith("\uFEFFfecha,actor,rol,accion,usuarioAfectado,detalles\n");
        assertThat(csv).contains("2026-10-27T10:00:00Z");
        assertThat(csv).contains("admin@test.local");
        assertThat(csv).contains("ADMIN");
        assertThat(csv).contains("ANOMALIA_RESUELTA");
        assertThat(csv).contains("00000000-0000-0000-0000-000000000010");
        assertThat(csv).contains("\"motivo=Texto, con \"\"comillas\"\"\"");
    }

    @Test
    void formatsEmptyReportWithHeaderOnly() {
        String csv = AuditCsvFormatter.format(List.of());

        assertThat(csv).isEqualTo("\uFEFFfecha,actor,rol,accion,usuarioAfectado,detalles\n");
    }
}
