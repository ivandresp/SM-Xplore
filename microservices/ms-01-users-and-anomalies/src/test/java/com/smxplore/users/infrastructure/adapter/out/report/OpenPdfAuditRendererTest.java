package com.smxplore.users.infrastructure.adapter.out.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.AuditEntry;
import com.smxplore.users.domain.model.Role;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenPdfAuditRendererTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final OpenPdfAuditRenderer renderer = new OpenPdfAuditRenderer();

    @Test
    void rendersAValidPdfWithTheAuditRows() {
        AuditEntry entry = AuditEntry.create(
                UUID.randomUUID(),
                "admin@smxplore.local",
                Role.ADMIN,
                AuditAction.PRESTADORA_APROBADA,
                UUID.randomUUID(),
                Map.of("motivo", "Documentos válidos"),
                NOW);

        byte[] pdf = renderer.render(List.of(entry), criteria(), NOW);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void rendersAnEmptyReportWithoutFailing() {
        byte[] pdf = renderer.render(List.of(), criteria(), NOW);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    private static AuditSearchCriteria criteria() {
        return new AuditSearchCriteria(null, AuditAction.PRESTADORA_APROBADA, null, null, null, 0, 100);
    }
}
