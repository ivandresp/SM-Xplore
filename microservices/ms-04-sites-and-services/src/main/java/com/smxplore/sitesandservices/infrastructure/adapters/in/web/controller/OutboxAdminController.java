package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.JwtUserResolver;
import com.smxplore.sitesandservices.infrastructure.adapters.out.outbox.OutboxReprocessResult;
import com.smxplore.sitesandservices.infrastructure.adapters.out.outbox.OutboxReprocessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * MS04-10: el reproceso exige autenticacion (ADMIN en SecurityConfiguration), acepta limite y
 * dry-run, y queda auditado por el servicio.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/outbox")
@Tag(name = "Outbox administration", description = "Operational outbox reprocessing.")
public class OutboxAdminController {

    private final OutboxReprocessService reprocessService;
    private final JwtUserResolver jwtUserResolver;

    @PostMapping("/reprocess")
    @Operation(summary = "Reset FAILED outbox events to PENDING in PostgreSQL and MongoDB")
    public Mono<OutboxReprocessResult> reprocessFailed(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return reprocessService.reprocessFailed(limit, dryRun, actor);
    }
}
