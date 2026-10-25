package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.AddReservationItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.AddReservationItemsUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultOneReservationItemUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultOneReservationUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationItemsByTypeUseCasePort;
import com.smxplore.activitiesevents.domain.ports.usecases.reservation.ConsultReservationItemsUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.PaginationLimits;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReservationItemRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReservationItemResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReservationItemsRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper.ReservationItemWebMapper;
import com.smxplore.activitiesevents.infrastructure.config.JwtConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Reservation Items")
public class ReservationItemController {

    private final AddReservationItemUseCasePort addReservationItemUseCase;
    private final AddReservationItemsUseCasePort addReservationItemsUseCase;
    private final ConsultReservationItemsUseCasePort consultReservationItemsUseCase;
    private final ConsultOneReservationItemUseCasePort consultOneReservationItemUseCase;
    private final ConsultReservationItemsByTypeUseCasePort consultReservationItemsByTypeUseCase;
    private final ConsultOneReservationUseCasePort consultOneReservationUseCase;
    private final ReservationItemWebMapper mapper;

    @PostMapping("/reservations/{reservationId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add one item to a reservation (its owner or staff)")
    public Mono<ReservationItemResponse> addItem(@PathVariable UUID reservationId,
                                                 @Valid @RequestBody ReservationItemRequest request,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return requireReservationOwnerOrStaff(jwt, reservationId)
                .then(Mono.defer(() -> addReservationItemUseCase.handle(reservationId, mapper.toDomain(request))))
                .map(mapper::toResponse);
    }

    @PostMapping("/reservations/{reservationId}/items/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add several items of the same type to a reservation (its owner or staff)")
    public Flux<ReservationItemResponse> addItems(@PathVariable UUID reservationId,
                                                  @Valid @RequestBody ReservationItemsRequest request,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return requireReservationOwnerOrStaff(jwt, reservationId)
                .thenMany(Flux.defer(() -> addReservationItemsUseCase.handle(reservationId,
                        mapper.toDomainList(request.items()))))
                .map(mapper::toResponse);
    }

    @GetMapping("/reservations/{reservationId}/items")
    @Operation(summary = "List the items of a reservation (owner, provider, guide or staff)")
    public Flux<ReservationItemResponse> findByReservation(@PathVariable UUID reservationId,
                                                           @AuthenticationPrincipal Jwt jwt) {
        return requireReservationAccess(jwt, reservationId)
                .thenMany(consultReservationItemsUseCase.handle(reservationId).map(mapper::toResponse));
    }

    @GetMapping("/reservation-items/{reservationItemId}")
    @Operation(summary = "Get one reservation item by id (owner, provider, guide or staff)")
    public Mono<ReservationItemResponse> findOne(@PathVariable UUID reservationItemId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return consultOneReservationItemUseCase.handle(reservationItemId)
                .flatMap(item -> requireReservationAccess(jwt, item.getReservationId()).thenReturn(item))
                .map(mapper::toResponse);
    }

    @GetMapping("/reservation-items")
    @Operation(summary = "List reservation items by type (admin/managers)")
    public Flux<ReservationItemResponse> findByType(@RequestParam ReservationItemType type,
                                                    @RequestParam(defaultValue = "50") int limit) {
        return consultReservationItemsByTypeUseCase.handle(type, PaginationLimits.clamp(limit))
                .map(mapper::toResponse);
    }

    private static boolean isAdminOrManager(Jwt jwt) {
        var role = jwt.getClaimAsString(JwtConfiguration.ROLE_CLAIM);
        return UserRole.ADMIN.name().equals(role) || UserRole.DESTINATION_MANAGER.name().equals(role);
    }

    private Mono<Void> requireReservationOwnerOrStaff(Jwt jwt, UUID reservationId) {
        if (isAdminOrManager(jwt)) {
            return Mono.empty();
        }
        return consultOneReservationUseCase.handle(reservationId)
                .doOnNext(reservation -> {
                    if (reservation.getTouristId() == null
                            || !reservation.getTouristId().toString().equals(jwt.getSubject())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Only the owner of the reservation can change its items.");
                    }
                })
                .then();
    }

    private Mono<Void> requireReservationAccess(Jwt jwt, UUID reservationId) {
        if (isAdminOrManager(jwt)) {
            return Mono.empty();
        }
        return consultOneReservationUseCase.handle(reservationId)
                .doOnNext(reservation -> ensureAccess(jwt, reservation))
                .then();
    }

    private static void ensureAccess(Jwt jwt, Reservation reservation) {
        var subject = jwt.getSubject();
        var isOwner = reservation.getTouristId() != null && reservation.getTouristId().toString().equals(subject);
        var isProvider = reservation.getProviderId() != null && reservation.getProviderId().toString().equals(subject);
        var isGuide = reservation.getGuideId() != null && reservation.getGuideId().toString().equals(subject);
        if (!isOwner && !isProvider && !isGuide) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this reservation.");
        }
    }
}
