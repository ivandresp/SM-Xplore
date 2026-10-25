package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

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
        var activity = mapper.toDomain(request).toBuilder()
                .guideId(resolveGuideId(jwt, request.guideId()))
                .build();
        return registerActivityUseCase.handle(activity).map(mapper::toResponse);
    }

    @GetMapping("/{activityId}")
    @Operation(summary = "Get one activity by id")
    public Mono<ActivityResponse> findOne(@PathVariable UUID activityId) {
        return viewActivityDetailsUseCase.handle(activityId).map(mapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List all activities")
    public Flux<ActivityResponse> findAll() {
        return consultActivitiesUseCase.handle().map(mapper::toResponse);
    }

    @GetMapping("/search")
    @Operation(summary = "List activities by category")
    public Flux<ActivityResponse> findByCategory(@RequestParam String category) {
        return findActivitiesByCategoryUseCase.handle(category).map(mapper::toResponse);
    }

    @GetMapping("/site/{siteId}")
    @Operation(summary = "List the activities of a site")
    public Flux<ActivityResponse> findBySite(@PathVariable UUID siteId) {
        return findActivitiesBySiteUseCase.handle(siteId).map(mapper::toResponse);
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
