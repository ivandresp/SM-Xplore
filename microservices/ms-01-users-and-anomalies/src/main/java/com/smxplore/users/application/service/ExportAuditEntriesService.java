package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.AuditReport;
import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.application.port.in.ExportAuditEntriesUseCase;
import com.smxplore.users.application.port.out.AuditLogRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import reactor.core.publisher.Mono;

public final class ExportAuditEntriesService implements ExportAuditEntriesUseCase {

    static final int MAX_ROWS = 10_000;
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final AuditLogRepository auditLog;
    private final Clock clock;

    public ExportAuditEntriesService(AuditLogRepository auditLog, Clock clock) {
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public Mono<AuditReport> export(AuditSearchCriteria criteria) {
        return auditLog.export(criteria, MAX_ROWS)
                .collectList()
                .map(entries -> new AuditReport(
                        "auditoria-" + FILE_TIMESTAMP.format(clock.instant()) + ".csv",
                        AuditCsvFormatter.format(entries).getBytes(StandardCharsets.UTF_8),
                        entries.size()));
    }
}
