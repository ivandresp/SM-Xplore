package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.AuditEntryView;
import com.smxplore.users.application.dto.AuditSearchCriteria;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.port.in.ListAuditEntriesUseCase;
import com.smxplore.users.domain.model.AuditAction;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final ListAuditEntriesUseCase listAuditEntries;

    public AdminAuditController(ListAuditEntriesUseCase listAuditEntries) {
        this.listAuditEntries = listAuditEntries;
    }

    @GetMapping
    public Mono<PageResult<AuditEntryView>> list(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID targetUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listAuditEntries.list(new AuditSearchCriteria(actorId, action, targetUserId, from, to, page, size));
    }
}
