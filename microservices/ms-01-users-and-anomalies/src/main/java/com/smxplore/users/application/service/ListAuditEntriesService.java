package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.AuditEntryView;
import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.port.in.ListAuditEntriesUseCase;
import com.smxplore.users.application.port.out.AuditLogRepository;
import reactor.core.publisher.Mono;

public final class ListAuditEntriesService implements ListAuditEntriesUseCase {

    private final AuditLogRepository auditLog;

    public ListAuditEntriesService(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public Mono<PageResult<AuditEntryView>> list(AuditSearchCriteria criteria) {
        return auditLog.search(criteria)
                .map(page -> PageResult.of(
                        page.content().stream().map(AuditEntryView::from).toList(),
                        page.page(),
                        page.size(),
                        page.totalElements()));
    }
}
