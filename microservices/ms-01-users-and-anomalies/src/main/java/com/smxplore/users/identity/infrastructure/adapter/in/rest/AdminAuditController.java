package com.smxplore.users.identity.infrastructure.adapter.in.rest;

import com.smxplore.users.identity.application.dto.AuditEntryView;
import com.smxplore.users.identity.application.dto.AuditSearchCriteria;
import com.smxplore.users.identity.application.dto.PageResult;
import com.smxplore.users.identity.application.port.in.ListAuditEntriesUseCase;
import com.smxplore.users.identity.domain.model.AuditAction;
import com.smxplore.users.identity.domain.model.UserId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final ListAuditEntriesUseCase listAuditEntries;

    public AdminAuditController(ListAuditEntriesUseCase listAuditEntries) {
        this.listAuditEntries = listAuditEntries;
    }

    @GetMapping
    public PageResult<AuditEntryView> list(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID targetUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listAuditEntries.list(new AuditSearchCriteria(
                actorId == null ? null : new UserId(actorId),
                action,
                targetUserId == null ? null : new UserId(targetUserId),
                from,
                to,
                page,
                size));
    }
}
