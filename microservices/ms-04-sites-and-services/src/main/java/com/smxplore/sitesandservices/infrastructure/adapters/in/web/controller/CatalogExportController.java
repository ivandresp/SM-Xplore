package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.domain.model.catalog.CatalogEntity;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.ports.in.ExportCatalogUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.catalog.CatalogExportPageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.CatalogWebMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/catalog")
@Tag(
        name = "Catalog export",
        description = "Paginated catalog feed used to rebuild downstream projections."
)
public class CatalogExportController {

    private final ExportCatalogUseCase exportCatalogUseCase;
    private final CatalogWebMapper webMapper;
    private final JwtUserResolver jwtUserResolver;

    @GetMapping("/export")
    @Operation(summary = "Export catalog pages with the same payload published by the catalog events")
    public Mono<CatalogExportPageResponse> export(
            @RequestParam CatalogEntity entity,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedSince,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return exportCatalogUseCase.handle(entity, updatedSince, page, size, actor)
                .map(webMapper::toResponse);
    }
}
