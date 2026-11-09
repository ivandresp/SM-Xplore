package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

import com.smxplore.activitiesevents.application.events.ResolveEventSiteUseCase;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventFilter;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ChangeEventStatusUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ConsultEventsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.EstimateEventAttendanceUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.RegisterEventUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.SearchEventsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ValidateEventExistsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ViewEventDetailsUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ChangeEventStatusRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.EstimateAttendanceRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.EventRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.EventResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.PageResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.PaginationLimits;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper.EventWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Events hosted at the tourist sites")
public class EventController {

    private final RegisterEventUseCasePort registerEventUseCase;
    private final ResolveEventSiteUseCase resolveEventSiteUseCase;
    private final ViewEventDetailsUseCasePort viewEventDetailsUseCase;
    private final ConsultEventsUseCasePort consultEventsUseCase;
    private final SearchEventsUseCasePort searchEventsUseCase;
    private final ChangeEventStatusUseCasePort changeEventStatusUseCase;
    private final EstimateEventAttendanceUseCasePort estimateEventAttendanceUseCase;
    private final ValidateEventExistsUseCasePort validateEventExistsUseCase;
    private final SiteCatalogPort siteCatalog;
    private final EventWebMapper mapper;

    @Value("${app.security.jwt.role-claim:role}")
    private String roleClaim;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an event for a site")
    public Mono<EventResponse> register(@Valid @RequestBody EventRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        var event = mapper.toDomain(request);
        return resolveEventSiteUseCase.handle(event, managerIdFrom(jwt), role(jwt), jwt.getTokenValue())
                .flatMap(registerEventUseCase::handle)
                .map(mapper::toResponse);
    }

    private static UUID managerIdFrom(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token subject is required.");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject.");
        }
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get one event by id")
    public Mono<EventResponse> findOne(@PathVariable UUID eventId,
                                        @AuthenticationPrincipal Jwt jwt) {
        return viewEventDetailsUseCase.handle(eventId)
                .filter(event -> event.getStatus() == EventStatus.PUBLISHED
                        || canSeeUnpublished(event, jwt))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.")))
                .map(mapper::toResponse);
    }

    private boolean canSeeUnpublished(Event event, Jwt jwt) {
        var role = role(jwt);
        if (role == UserRole.ADMIN) {
            return true;
        }
        return role == UserRole.DESTINATION_MANAGER
                && jwt.getSubject() != null
                && event.getManagerId() != null
                && event.getManagerId().toString().equals(jwt.getSubject());
    }

    @GetMapping("/{eventId}/exists")
    @Operation(summary = "Validate that an event exists and is visible to the caller")
    public Mono<ResponseEntity<Void>> exists(@PathVariable UUID eventId,
                                             @AuthenticationPrincipal Jwt jwt) {
        return viewEventDetailsUseCase.handle(eventId)
                .filter(event -> event.getStatus() == EventStatus.PUBLISHED
                        || canSeeUnpublished(event, jwt))
                .map(event -> ResponseEntity.ok().<Void>build())
                .defaultIfEmpty(ResponseEntity.<Void>notFound().build());
    }

    @GetMapping
    @Operation(summary = "List and filter events")
    public Mono<PageResponse<EventResponse>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID managerId,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) Instant startsAfter,
            @RequestParam(required = false) Instant startsBefore,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        PaginationLimits.validatePageSize(page, size);
        var normalizedSearch = PaginationLimits.validateSearch(search);
        var filter = new EventFilter(normalizedSearch, resolveStatus(status, managerId, jwt), category, siteId, managerId,
                priceMin, priceMax, startsAfter, startsBefore, sort, page, size);
        return consultEventsUseCase.handle(filter)
                .map(result -> PageResponse.from(result, mapper::toResponse));
    }

    /**
     * El catálogo público solo ve lo publicado; la gestora de un evento puede
     * listar todo lo suyo y el admin, cualquier estado.
     */
    private EventStatus resolveStatus(EventStatus requested, UUID managerId, Jwt jwt) {
        var role = role(jwt);
        var owns = managerId != null && jwt != null && jwt.getSubject() != null
                && managerId.toString().equals(jwt.getSubject());
        if (requested == null) {
            return role == UserRole.DESTINATION_MANAGER && owns ? null : EventStatus.PUBLISHED;
        }
        if (requested == EventStatus.PUBLISHED
                || role == UserRole.ADMIN
                || (role == UserRole.DESTINATION_MANAGER && owns)) {
            return requested;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only the manager of record or an admin can list unpublished events.");
    }

    @GetMapping("/search")
    @Operation(summary = "Search events by name")
    public Mono<PageResponse<EventResponse>> search(@RequestParam String name,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        PaginationLimits.validatePageSize(page, size);
        var normalizedName = PaginationLimits.validateSearch(name, "name");
        return searchEventsUseCase.handle(normalizedName, page, size)
                .map(result -> PageResponse.from(result, mapper::toResponse));
    }

    @PatchMapping("/{eventId}/status")
    @Operation(summary = "Change the status of an event")
    public Mono<EventResponse> changeStatus(@PathVariable UUID eventId,
                                            @Valid @RequestBody ChangeEventStatusRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return requireCanManage(eventId, jwt)
                .then(Mono.defer(() -> changeEventStatusUseCase.handle(eventId, request.status())))
                .map(mapper::toResponse);
    }

    @PatchMapping("/{eventId}/attendance")
    @Operation(summary = "Estimate the expected attendance of an event")
    public Mono<EventResponse> estimateAttendance(@PathVariable UUID eventId,
                                                  @Valid @RequestBody EstimateAttendanceRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return requireCanManage(eventId, jwt)
                .then(Mono.defer(() -> estimateEventAttendanceUseCase.handle(eventId, request.expectedAttendance())))
                .map(mapper::toResponse);
    }

    private UserRole role(Jwt jwt) {
        return jwt == null ? null : UserRole.fromClaim(jwt.getClaimAsString(roleClaim));
    }

    private Mono<Void> requireCanManage(UUID eventId, Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required."));
        }
        var role = role(jwt);
        if (role == UserRole.ADMIN) {
            return Mono.empty();
        }
        if (role != UserRole.DESTINATION_MANAGER) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the manager of the event or an admin can modify it."));
        }
        return viewEventDetailsUseCase.handle(eventId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.")))
                .flatMap(event -> requireCurrentSiteManager(event, jwt));
    }

    /**
     * El {@code managerId} del evento es un snapshot que solo se refresca con {@code
     * catalog.site.changed}; mientras eso no pasa, una gestora cesada conservaria el permiso. Se
     * revalida contra ms-04 en cada escritura con el token del usuario (si ms-04 no responde se
     * falla cerrado con 503 en lugar de aceptar el snapshot viejo).
     */
    private Mono<Void> requireCurrentSiteManager(Event event, Jwt jwt) {
        return siteCatalog.findById(event.getSiteId(), jwt.getTokenValue())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only the manager of the event or an admin can modify it.")))
                .flatMap(site -> site.managerId() != null
                        && site.managerId().toString().equals(jwt.getSubject())
                        ? Mono.<Void>empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only the manager of the event or an admin can modify it.")))
                .onErrorMap(error -> error instanceof ResponseStatusException ? error
                        : new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Cannot verify the event site right now.", error));
    }
}
