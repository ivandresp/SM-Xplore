package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

import com.smxplore.activitiesevents.domain.model.catalog.CatalogEntity;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.usecases.catalog.ExportCatalogUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.catalog.CatalogExportPageResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper.CatalogWebMapper;
import com.smxplore.activitiesevents.infrastructure.config.JwtProperties;
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

/**
 * Export feed del catálogo de ms-03 (actividades y eventos) para que ms-04 reindexe su buscador.
 *
 * <p>La ruta cae bajo {@code /api/v1/admin/**}, que la cadena de seguridad ya restringe a ADMIN.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/catalog")
@Tag(name = "Catalog export", description = "Incremental export feed of activities and events.")
public class CatalogExportController {

    private final ExportCatalogUseCasePort exportCatalog;
    private final CatalogWebMapper mapper;
    private final JwtProperties jwtProperties;

    @GetMapping("/export")
    @Operation(summary = "Export activities or events updated since an instant")
    public Mono<CatalogExportPageResponse> export(
            @RequestParam CatalogEntity entity,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedSince,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return exportCatalog.handle(entity, updatedSince, page, size, role(jwt))
                .map(mapper::toResponse);
    }

    private String role(Jwt jwt) {
        var role = UserRole.fromClaim(jwt.getClaimAsString(jwtProperties.getRoleClaim()));
        return role == null ? null : role.name();
    }
}
