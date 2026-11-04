package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.infrastructure.adapters.out.outbox.OutboxReprocessResult;
import com.smxplore.sitesandservices.infrastructure.adapters.out.outbox.OutboxReprocessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/outbox")
@Tag(name = "Outbox administration", description = "Operational outbox reprocessing.")
public class OutboxAdminController {

    private final OutboxReprocessService reprocessService;

    @PostMapping("/reprocess")
    @Operation(summary = "Reset FAILED outbox events to PENDING in PostgreSQL and MongoDB")
    public Mono<OutboxReprocessResult> reprocessFailed() {
        return reprocessService.reprocessFailed();
    }
}
