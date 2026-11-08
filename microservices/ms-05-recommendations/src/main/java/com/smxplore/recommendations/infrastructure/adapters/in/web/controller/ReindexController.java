package com.smxplore.recommendations.infrastructure.adapters.in.web.controller;

import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;
import com.smxplore.recommendations.domain.model.reindex.ReindexResult;
import com.smxplore.recommendations.domain.ports.in.ReindexCatalogUseCase;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.ReindexResponse;
import com.smxplore.recommendations.infrastructure.adapters.in.web.security.AuthenticatedUser;
import com.smxplore.recommendations.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/admin")
@Tag(name = "Reindex", description = "Rebuilds the catalog projection from ms-04 (categories, sites, services) and ms-03 (activities, events).")
public class ReindexController {

    private final ReindexCatalogUseCase reindexCatalogUseCase;
    private final JwtUserResolver jwtUserResolver;

    @PostMapping("/reindex")
    @Operation(summary = "Reindex a catalog entity from the admin catalog export of ms-04 or ms-03")
    public Mono<ReindexResponse> reindex(
            @RequestParam CatalogEntity entity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedSince,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION) String authorization,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AuthenticatedUser actor = jwtUserResolver.resolve(jwt);
        return reindexCatalogUseCase.handle(entity, updatedSince, authorization, actor.touristId())
                .map(ReindexController::toResponse);
    }

    private static ReindexResponse toResponse(ReindexResult result) {
        return new ReindexResponse(
                result.entity(),
                result.pages(),
                result.events(),
                result.applied(),
                result.duplicate(),
                result.stale());
    }
}
