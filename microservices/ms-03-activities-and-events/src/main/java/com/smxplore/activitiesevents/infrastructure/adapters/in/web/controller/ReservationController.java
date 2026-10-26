package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

import com.smxplore.activitiesevents.domain.exceptions.activity.ActivityNotFoundException;
import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CancelReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultOneReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByGuideUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByStatusUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationsByTouristUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.CreateReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.RegisterReservationAttendanceUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.RegisterReservationNoShowUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.PaginationLimits;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReservationRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReservationResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper.ReservationWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations")
public class ReservationController {

    private final CreateReservationUseCasePort createReservationUseCase;
    private final ConsultOneReservationUseCasePort consultOneReservationUseCase;
    private final ConsultReservationsByStatusUseCasePort consultReservationsByStatusUseCase;
    private final ConsultReservationsByTouristUseCasePort consultReservationsByTouristUseCase;
    private final ConsultReservationsByGuideUseCasePort consultReservationsByGuideUseCase;
    private final ConsultReservationsByItemUseCasePort consultReservationsByItemUseCase;
    private final CancelReservationUseCasePort cancelReservationUseCase;
    private final RegisterReservationAttendanceUseCasePort registerReservationAttendanceUseCase;
    private final RegisterReservationNoShowUseCasePort registerReservationNoShowUseCase;
    private final ActivityRepositoryPort activityRepository;
    private final EventRepositoryPort eventRepository;
    private final ReservationWebMapper mapper;

    @Value("${app.security.jwt.role-claim:role}")
    private String roleClaim;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a symbolic reservation (tourists create their own; admins may book for anyone)")
    public Mono<ReservationResponse> create(@Valid @RequestBody ReservationRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        if (!isAdmin(jwt) && !subject(jwt).equals(request.touristId().toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Tourists can only create their own reservations.");
        }
        var reservation = mapper.toDomain(request);
        if (!isAdmin(jwt)) {
            reservation = reservation.toBuilder().touristId(UUID.fromString(subject(jwt))).build();
        }
        return createReservationUseCase.handle(reservation).map(mapper::toResponse);
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "Get one reservation by id (owner, guide or staff)")
    public Mono<ReservationResponse> findOne(@PathVariable UUID reservationId, @AuthenticationPrincipal Jwt jwt) {
        return consultOneReservationUseCase.handle(reservationId)
                .doOnNext(reservation -> requireReservationAccess(jwt, reservation))
                .map(mapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List reservations by status (admin/managers)")
    public Flux<ReservationResponse> findByStatus(@RequestParam ReservationStatus status,
                                                  @RequestParam(defaultValue = "50") int limit) {
        return consultReservationsByStatusUseCase.handle(status, PaginationLimits.clamp(limit))
                .map(mapper::toResponse);
    }

    @GetMapping("/tourists/{touristId}")
    @Operation(summary = "List the reservations of a tourist (self or staff)")
    public Flux<ReservationResponse> findByTourist(@PathVariable UUID touristId,
                                                   @RequestParam(defaultValue = "50") int limit,
                                                   @AuthenticationPrincipal Jwt jwt) {
        requireSelfOrStaff(jwt, touristId, "You can only list your own reservations.");
        return consultReservationsByTouristUseCase.handle(touristId, PaginationLimits.clamp(limit))
                .map(mapper::toResponse);
    }

    @GetMapping("/guides/{guideId}")
    @Operation(summary = "List the reservations received by a guide (self or staff)")
    public Flux<ReservationResponse> findByGuide(@PathVariable UUID guideId,
                                                 @RequestParam(required = false) ReservationStatus status,
                                                 @RequestParam(defaultValue = "50") int limit,
                                                 @AuthenticationPrincipal Jwt jwt) {
        requireSelfOrStaff(jwt, guideId, "You can only list your own received reservations.");
        return consultReservationsByGuideUseCase.handle(guideId, status, PaginationLimits.clamp(limit))
                .map(mapper::toResponse);
    }

    @GetMapping("/by-item")
    @Operation(summary = "List the reservations of a given event or activity")
    public Flux<ReservationResponse> findByItem(@RequestParam ReservationItemType itemType,
                                                @RequestParam UUID itemId,
                                                @RequestParam(defaultValue = "50") int limit,
                                                @AuthenticationPrincipal Jwt jwt) {
        return requireItemAccess(jwt, itemType, itemId)
                .thenMany(Flux.defer(() -> consultReservationsByItemUseCase.handle(itemId, itemType,
                        PaginationLimits.clamp(limit))))
                .map(mapper::toResponse);
    }

    @PatchMapping("/{reservationId}/cancel")
    @Operation(summary = "Cancel a reservation (the tourist up to 24 h before the start; staff any time)")
    public Mono<ReservationResponse> cancel(@PathVariable UUID reservationId, @AuthenticationPrincipal Jwt jwt) {
        return consultOneReservationUseCase.handle(reservationId)
                .flatMap(reservation -> {
                    var staff = isStaff(jwt, reservation);
                    if (!staff && !isOwner(jwt, reservation)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Only the owner of the reservation can cancel it.");
                    }
                    return cancelReservationUseCase.handle(reservationId, staff);
                })
                .map(mapper::toResponse);
    }

    @PatchMapping("/{reservationId}/attendance")
    @Operation(summary = "Register the attendance to a confirmed reservation (its guide or staff)")
    public Mono<ReservationResponse> registerAttendance(@PathVariable UUID reservationId,
                                                        @AuthenticationPrincipal Jwt jwt) {
        return requireReservationOperator(jwt, reservationId)
                .then(Mono.defer(() -> registerReservationAttendanceUseCase.handle(reservationId)))
                .map(mapper::toResponse);
    }

    @PatchMapping("/{reservationId}/no-show")
    @Operation(summary = "Register the no show of a confirmed reservation (its guide or staff)")
    public Mono<ReservationResponse> registerNoShow(@PathVariable UUID reservationId,
                                                    @AuthenticationPrincipal Jwt jwt) {
        return requireReservationOperator(jwt, reservationId)
                .then(Mono.defer(() -> registerReservationNoShowUseCase.handle(reservationId)))
                .map(mapper::toResponse);
    }

    private static String subject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token subject is required.");
        }
        return jwt.getSubject();
    }

    private boolean isAdmin(Jwt jwt) {
        return jwt != null && UserRole.ADMIN.name().equals(jwt.getClaimAsString(roleClaim));
    }

    private boolean isAdminOrManager(Jwt jwt) {
        var role = jwt == null ? null : jwt.getClaimAsString(roleClaim);
        return UserRole.ADMIN.name().equals(role) || UserRole.DESTINATION_MANAGER.name().equals(role);
    }

    private void requireSelfOrStaff(Jwt jwt, UUID userId, String message) {
        if (!isAdminOrManager(jwt) && !userId.toString().equals(subject(jwt))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private void requireReservationAccess(Jwt jwt, Reservation reservation) {
        if (isAdminOrManager(jwt)) {
            return;
        }
        if (!isOwner(jwt, reservation) && !isStaff(jwt, reservation)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this reservation.");
        }
    }

    private boolean isOwner(Jwt jwt, Reservation reservation) {
        return reservation.getTouristId() != null
                && reservation.getTouristId().toString().equals(subject(jwt));
    }

    /** The guide of the activity or the manager of the event, plus admins and destination managers. */
    private boolean isStaff(Jwt jwt, Reservation reservation) {
        if (isAdminOrManager(jwt)) {
            return true;
        }
        var subject = subject(jwt);
        var isGuide = reservation.getGuideId() != null && reservation.getGuideId().toString().equals(subject);
        var isProvider = reservation.getProviderId() != null && reservation.getProviderId().toString().equals(subject);
        return isGuide || isProvider;
    }

    private Mono<Void> requireReservationOperator(Jwt jwt, UUID reservationId) {
        if (isAdminOrManager(jwt)) {
            return Mono.empty();
        }
        return consultOneReservationUseCase.handle(reservationId)
                .doOnNext(reservation -> {
                    var subject = subject(jwt);
                    var isGuide = reservation.getGuideId() != null
                            && reservation.getGuideId().toString().equals(subject);
                    if (!isGuide) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Only the guide of the reservation can operate it.");
                    }
                })
                .then();
    }

    private Mono<Void> requireItemAccess(Jwt jwt, ReservationItemType itemType, UUID itemId) {
        var staff = isAdminOrManager(jwt);
        return switch (itemType) {
            case ACTIVITY -> activityRepository.findById(itemId)
                    .switchIfEmpty(Mono.error(new ActivityNotFoundException(itemId)))
                    .filter(activity -> staff || (activity.getGuideId() != null
                            && activity.getGuideId().toString().equals(subject(jwt))))
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "You can only list reservations of your own activities.")))
                    .then();
            case EVENT -> {
                if (!staff) {
                    yield Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Only administrators or destination managers can list reservations of an event."));
                }
                yield eventRepository.findById(itemId)
                        .switchIfEmpty(Mono.error(new EventNotFoundException(itemId)))
                        .then();
            }
        };
    }
}
