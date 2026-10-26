package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.domain.model.AuditEntry;
import reactor.core.publisher.Mono;

public interface AuditLogRepository {

    Mono<AuditEntry> save(AuditEntry entry);

    Mono<PageResult<AuditEntry>> search(AuditSearchCriteria criteria);
}
