package com.smxplore.users.identity.application.port.out;

import com.smxplore.users.identity.application.dto.AuditSearchCriteria;
import com.smxplore.users.identity.application.dto.PageResult;
import com.smxplore.users.identity.domain.model.AuditEntry;

public interface AuditLogRepository {

    AuditEntry save(AuditEntry entry);

    PageResult<AuditEntry> search(AuditSearchCriteria criteria);
}
