package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.AuditReport;
import com.smxplore.users.application.dto.AuditSearchCriteria;
import reactor.core.publisher.Mono;

public interface ExportAuditEntriesUseCase {

    Mono<AuditReport> export(AuditSearchCriteria criteria);
}
