package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.domain.model.AuditEntry;
import java.time.Instant;
import java.util.List;

/**
 * Genera el PDF del reporte de auditoría. Vive en infraestructura porque depende de una librería de PDF.
 */
public interface AuditPdfRenderer {

    byte[] render(List<AuditEntry> entries, AuditSearchCriteria criteria, Instant generatedAt);
}
