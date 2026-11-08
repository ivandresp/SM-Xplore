package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

import com.smxplore.activitiesevents.domain.model.activity.ActivityFilter;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.CancelActivityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ChangeActivityStatusUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ConsultActivitiesUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.FindActivitiesByCategoryUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.FindActivitiesBySiteUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ManageAvailabilityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ManageSlotsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.RegisterActivityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.UpdateActivityUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.activity.ViewActivityDetailsUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ActivityRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ActivityResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.PageResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.PaginationLimits;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.AvailabilityRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ChangeActivityStatusRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.SlotsRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.UpdateActivityRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper.ActivityWebMapper;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.security.ActivityAccessGuard;
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
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
@Tag(name = "Activities", description = "Tourist activities offered at the sites")
public class ActivityController {

    private final RegisterActivityUseCasePort registerActivityUseCase;
    private final ViewActivityDetailsUseCasePort viewActivityDetailsUseCase;
    private final ConsultActivitiesUseCasePort consultActivitiesUseCase;
    private final FindActivitiesByCategoryUseCasePort findActivitiesByCategoryUseCase;
    private final FindActivitiesBySiteUseCasePort findActivitiesBySiteUseCase;
    private final UpdateActivityUseCasePort updateActivityUseCase;
    private final ManageAvailabilityUseCasePort manageAvailabilityUseCase;
    private final ManageSlotsUseCasePort manageSlotsUseCase;
    private final CancelActivityUseCasePort cancelActivityUseCase;
    private final ChangeActivityStatusUseCasePort changeActivityStatusUseCase;
    private final ActivityWebMapper mapper;
    private final ActivityAccessGuard accessGuard;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register an activity for a site")
    public Mono<ActivityResponse> register(@Valid @RequestBody ActivityRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        return accessGuard.requireCanCreate(request.siteId(), request.guideId(), jwt)
                .then(Mono.defer(() -> {
                    var activity = mapper.toDomain(request).toBuilder()
                            .guideId(resolveGuideId(jwt, request.guideId()))
                            .build();
                    return registerActivityUseCase.handle(activity);
                }))
                .map(mapper::toResponse);
    }

    @GetMapping("/{activityId}")
    @Operation(summary = "Get one activity by id")
    public Mono<ActivityResponse> findOne(@PathVariable UUID activityId,
                                          @AuthenticationPrincipal Jwt jwt) {
        return viewActivityDetailsUseCase.handle(activityId)
                .filter(activity -> activity.getStatus() == ActivityStatus.PUBLISHED
                        || accessGuard.canViewUnpublished(activity, jwt))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found.")))
                .map(mapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List and filter activities")
    public Mono<PageResponse<ActivityResponse>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ActivityStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) UUID guideId,
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
        var filter = new ActivityFilter(normalizedSearch, resolveStatus(status, guideId, jwt), category, siteId, guideId,
                priceMin, priceMax, startsAfter, startsBefore, sort, page, size);
        return consultActivitiesUseCase.handle(filter)
                .map(result -> PageResponse.from(result, mapper::toResponse));
    }

    /**
     * El catálogo público solo ve lo publicado. El guía dueño puede listar todo
     * lo suyo y el admin, cualquier estado; una gestora no enumera borradores
     * ajenos.
     */
    private ActivityStatus resolveStatus(ActivityStatus requested, UUID guideId, Jwt jwt) {
        var owns = ownsGuide(guideId, jwt);
        if (requested == null) {
            return owns ? null : ActivityStatus.PUBLISHED;
        }
        if (requested == ActivityStatus.PUBLISHED || owns || accessGuard.isAdmin(jwt)) {
            return requested;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only an admin or the guide of record can list unpublished activities.");
    }

    private static boolean ownsGuide(UUID guideId, Jwt jwt) {
        return guideId != null && jwt != null && jwt.getSubject() != null
                && guideId.toString().equals(jwt.getSubject());
    }

    @GetMapping("/search")
    @Operation(summary = "List activities by category")
    public Mono<PageResponse<ActivityResponse>> findByCategory(@RequestParam String category,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        PaginationLimits.validatePageSize(page, size);
        return findActivitiesByCategoryUseCase.handle(category, page, size)
                .map(result -> PageResponse.from(result, mapper::toResponse));
    }

    @GetMapping("/site/{siteId}")
    @Operation(summary = "List the activities of a site")
    public Mono<PageResponse<ActivityResponse>> findBySite(@PathVariable UUID siteId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        PaginationLimits.validatePageSize(page, size);
        return findActivitiesBySiteUseCase.handle(siteId, page, size)
                .map(result -> PageResponse.from(result, mapper::toResponse));
    }

    @PatchMapping("/{activityId}")
    @Operation(summary = "Update the details of an activity")
    public Mono<ActivityResponse> update(@PathVariable UUID activityId,
                                         @Valid @RequestBody UpdateActivityRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return accessGuard.requireCanManage(activityId, jwt)
                .then(Mono.defer(() -> updateActivityUseCase.handle(activityId, request.name(), request.category(), request.description(), request.price())))
                .map(mapper::toResponse);
    }

    @PatchMapping("/{activityId}/availability")
    @Operation(summary = "Change the availability window of an activity")
    public Mono<ActivityResponse> manageAvailability(@PathVariable UUID activityId,
                                                     @Valid @RequestBody AvailabilityRequest request,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return accessGuard.requireCanManage(activityId, jwt)
                .then(Mono.defer(() -> manageAvailabilityUseCase.handle(activityId, request.startsAt(), request.endsAt())))
                .map(mapper::toResponse);
    }

    @PatchMapping("/{activityId}/slots")
    @Operation(summary = "Change the number of slots of an activity")
    public Mono<ActivityResponse> manageSlots(@PathVariable UUID activityId,
                                              @Valid @RequestBody SlotsRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        return accessGuard.requireCanManage(activityId, jwt)
                .then(Mono.defer(() -> manageSlotsUseCase.handle(activityId, request.maxCapacity())))
                .map(mapper::toResponse);
    }

    @PostMapping("/{activityId}/cancel")
    @Operation(summary = "Cancel an activity")
    public Mono<ActivityResponse> cancel(@PathVariable UUID activityId,
                                         @AuthenticationPrincipal Jwt jwt) {
        return accessGuard.requireCanManage(activityId, jwt)
                .then(Mono.defer(() -> cancelActivityUseCase.handle(activityId)))
                .map(mapper::toResponse);
    }

    @PatchMapping("/{activityId}/status")
    @Operation(summary = "Change the status of an activity")
    public Mono<ActivityResponse> changeStatus(@PathVariable UUID activityId,
                                               @Valid @RequestBody ChangeActivityStatusRequest request,
                                               @AuthenticationPrincipal Jwt jwt) {
        return accessGuard.requireCanManage(activityId, jwt)
                .then(Mono.defer(() -> changeActivityStatusUseCase.handle(activityId, request.status())))
                .map(mapper::toResponse);
    }

    /**
     * Guides are always attributed from their own token; managers and admins may
     * assign a guide explicitly. A null principal only happens in sliced tests.
     */
    private UUID resolveGuideId(Jwt jwt, UUID requestedGuideId) {
        if (jwt == null || !accessGuard.isGuide(jwt)) {
            return requestedGuideId;
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid token subject");
        }
    }
}
