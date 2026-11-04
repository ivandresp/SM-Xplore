package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.zone.ZoneFilter;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.domain.ports.in.ChangeZoneStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListZonesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateZoneUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone.ChangeZoneStatusRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone.CreateZoneRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone.UpdateZoneRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone.ZoneResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.CatalogRequestMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.CatalogWebMapper;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/zones")
@Tag(name = "Zones", description = "Tourist zones stored in PostgreSQL.")
public class ZoneController {

    private final CreateZoneUseCase createUseCase;
    private final UpdateZoneUseCase updateUseCase;
    private final ChangeZoneStatusUseCase changeStatusUseCase;
    private final GetZoneUseCase getUseCase;
    private final ListZonesUseCase listUseCase;
    private final CatalogRequestMapper requestMapper;
    private final CatalogWebMapper webMapper;
    private final JwtUserResolver jwtUserResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a tourist zone")
    public Mono<ZoneResponse> create(
            @Valid @RequestBody CreateZoneRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return createUseCase.handle(requestMapper.toDomain(request), actor)
                .map(webMapper::toResponse);
    }

    @PutMapping("/{zoneId}")
    @Operation(summary = "Update a tourist zone")
    public Mono<ZoneResponse> update(
            @PathVariable UUID zoneId,
            @Valid @RequestBody UpdateZoneRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return updateUseCase.handle(requestMapper.toDomain(zoneId, request), actor)
                .map(webMapper::toResponse);
    }

    @PatchMapping("/{zoneId}/status")
    @Operation(summary = "Change a zone status, including the congested marker")
    public Mono<ZoneResponse> changeStatus(
            @PathVariable UUID zoneId,
            @Valid @RequestBody ChangeZoneStatusRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return changeStatusUseCase.handle(requestMapper.toZoneStatus(zoneId, request.status()), actor)
                .map(webMapper::toResponse);
    }

    @GetMapping("/{zoneId}")
    @Operation(summary = "Get a tourist zone")
    public Mono<ZoneResponse> get(@PathVariable UUID zoneId) {
        return getUseCase.handle(zoneId).map(webMapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List tourist zones")
    public Mono<PageResponse<ZoneResponse>> list(
            @RequestParam(required = false) ZoneStatus status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ZoneFilter filter = new ZoneFilter(status, type, search, page, size);
        return listUseCase.handle(filter)
                .map(result -> PageResponse.from(result, webMapper::toResponse));
    }
}
