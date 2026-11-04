package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.site.SiteFilter;
import com.smxplore.sitesandservices.domain.ports.in.AssignSiteZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ChangeSiteStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateSiteUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetSiteUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListSitesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateSiteUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.ChangeContentStatusRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site.AssignSiteZoneRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site.CreateSiteRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site.SiteResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site.UpdateSiteRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.CatalogRequestMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.CatalogWebMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.CorrelationIdResolver;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sites")
@Tag(name = "Sites", description = "Tourist attractions stored in MongoDB.")
public class SiteController {

    private final CreateSiteUseCase createUseCase;
    private final UpdateSiteUseCase updateUseCase;
    private final ChangeSiteStatusUseCase changeStatusUseCase;
    private final AssignSiteZoneUseCase assignZoneUseCase;
    private final GetSiteUseCase getUseCase;
    private final ListSitesUseCase listUseCase;
    private final CatalogRequestMapper requestMapper;
    private final CatalogWebMapper webMapper;
    private final JwtUserResolver jwtUserResolver;
    private final CorrelationIdResolver correlationIdResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a tourist site")
    public Mono<SiteResponse> create(
            @Valid @RequestBody CreateSiteRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return createUseCase.handle(
                        requestMapper.toDomain(request),
                        actor,
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }

    @PutMapping("/{siteId}")
    @Operation(summary = "Update a tourist site")
    public Mono<SiteResponse> update(
            @PathVariable UUID siteId,
            @Valid @RequestBody UpdateSiteRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return updateUseCase.handle(
                        requestMapper.toDomain(siteId, request),
                        actor,
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }

    @PatchMapping("/{siteId}/status")
    @Operation(summary = "Change a site status")
    public Mono<SiteResponse> changeStatus(
            @PathVariable UUID siteId,
            @Valid @RequestBody ChangeContentStatusRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return changeStatusUseCase.handle(
                        requestMapper.toSiteStatus(siteId, request.status()),
                        actor,
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }

    @PatchMapping("/{siteId}/zone")
    @Operation(summary = "Assign a site to a tourist zone")
    public Mono<SiteResponse> assignZone(
            @PathVariable UUID siteId,
            @Valid @RequestBody AssignSiteZoneRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return assignZoneUseCase.handle(
                        requestMapper.toSiteZone(siteId, request.zoneId()),
                        actor,
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }

    @GetMapping("/{siteId}")
    @Operation(summary = "Get a tourist site")
    public Mono<SiteResponse> get(@PathVariable UUID siteId) {
        return getUseCase.handle(siteId).map(webMapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List tourist sites with filters")
    public Mono<PageResponse<SiteResponse>> list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        SiteFilter filter = new SiteFilter(categoryId, zoneId, status, search, page, size);
        return listUseCase.handle(filter)
                .map(result -> PageResponse.from(result, webMapper::toResponse));
    }
}
