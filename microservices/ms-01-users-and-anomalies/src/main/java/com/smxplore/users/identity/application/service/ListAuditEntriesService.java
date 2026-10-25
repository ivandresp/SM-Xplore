package com.smxplore.users.identity.application.service;

import com.smxplore.users.identity.application.dto.AuditEntryView;
import com.smxplore.users.identity.application.dto.AuditSearchCriteria;
import com.smxplore.users.identity.application.dto.PageResult;
import com.smxplore.users.identity.application.port.in.ListAuditEntriesUseCase;
import com.smxplore.users.identity.application.port.out.AuditLogRepository;
import com.smxplore.users.identity.domain.model.AuditEntry;

public final class ListAuditEntriesService implements ListAuditEntriesUseCase {

    private final AuditLogRepository auditLog;

    public ListAuditEntriesService(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public PageResult<AuditEntryView> list(AuditSearchCriteria criteria) {
        PageResult<AuditEntry> page = auditLog.search(criteria);
        return PageResult.of(
                page.content().stream().map(AuditEntryView::from).toList(),
                page.page(),
                page.size(),
                page.totalElements());
    }
}
