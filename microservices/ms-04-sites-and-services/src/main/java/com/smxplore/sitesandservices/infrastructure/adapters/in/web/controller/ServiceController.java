package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.service.ServiceFilter;
import com.smxplore.sitesandservices.domain.ports.in.ChangeServiceStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateServiceUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetServiceUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListServicesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateServiceUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.ChangeContentStatusRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service.CreateServiceRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service.ServiceResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service.UpdateServiceRequest;
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
@RequestMapping("/api/v1/services")
@Tag(name = "Services", description = "Tourist services stored in MongoDB.")
public class ServiceController {

    private final CreateServiceUseCase createUseCase;
    private final UpdateServiceUseCase updateUseCase;
    private final ChangeServiceStatusUseCase changeStatusUseCase;
    private final GetServiceUseCase getUseCase;
    private final ListServicesUseCase listUseCase;
    private final CatalogRequestMapper requestMapper;
    private final CatalogWebMapper webMapper;
    private final JwtUserResolver jwtUserResolver;
    private final CorrelationIdResolver correlationIdResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a tourist service")
    public Mono<ServiceResponse> create(
            @Valid @RequestBody CreateServiceRequest request,
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

    @PutMapping("/{serviceId}")
    @Operation(summary = "Update a tourist service")
    public Mono<ServiceResponse> update(
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateServiceRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return updateUseCase.handle(
                        requestMapper.toDomain(serviceId, request),
                        actor,
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }

    @PatchMapping("/{serviceId}/status")
    @Operation(summary = "Change a service status")
    public Mono<ServiceResponse> changeStatus(
            @PathVariable UUID serviceId,
            @Valid @RequestBody ChangeContentStatusRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return changeStatusUseCase.handle(
                        requestMapper.toServiceStatus(serviceId, request.status()),
                        actor,
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }

    @GetMapping("/{serviceId}")
    @Operation(summary = "Get a tourist service")
    public Mono<ServiceResponse> get(@PathVariable UUID serviceId) {
        return getUseCase.handle(serviceId).map(webMapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List tourist services with filters")
    public Mono<PageResponse<ServiceResponse>> list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID providerId,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ServiceFilter filter = new ServiceFilter(
                categoryId, zoneId, siteId, providerId, status, type, search, page, size);
        return listUseCase.handle(filter)
                .map(result -> PageResponse.from(result, webMapper::toResponse));
    }
}
