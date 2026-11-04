package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationFilter;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import com.smxplore.sitesandservices.domain.ports.in.ChangeReservationStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CheckReservationAvailabilityUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateReservationUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetReservationUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListReservationsUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.ChangeReservationStatusRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.CreateReservationRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.ReservationAvailabilityResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.ReservationResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.ReservationRequestMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.ReservationWebMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.CorrelationIdResolver;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.JwtUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Symbolic service reservations stored in PostgreSQL.")
public class ReservationController {

    private final CreateReservationUseCase createUseCase;
    private final GetReservationUseCase getUseCase;
    private final ListReservationsUseCase listUseCase;
    private final ChangeReservationStatusUseCase changeStatusUseCase;
    private final CheckReservationAvailabilityUseCase availabilityUseCase;
    private final ReservationRequestMapper requestMapper;
    private final ReservationWebMapper webMapper;
    private final JwtUserResolver jwtUserResolver;
    private final CorrelationIdResolver correlationIdResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a confirmed symbolic reservation for one provider's services")
    public Mono<ReservationResponse> create(
            @Valid @RequestBody CreateReservationRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return createUseCase.handle(
                        requestMapper.toDomain(request),
                        idempotencyKey,
                        actor,
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }

    @GetMapping("/availability")
    @Operation(summary = "Check service availability and remaining capacity for a date")
    public Mono<ReservationAvailabilityResponse> availability(
            @RequestParam UUID serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant bookedFor,
            @RequestParam(defaultValue = "1") int people
    ) {
        return availabilityUseCase.handle(serviceId, bookedFor, people)
                .map(webMapper::toResponse);
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "Get a reservation")
    public Mono<ReservationResponse> get(
            @PathVariable UUID reservationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return getUseCase.handle(reservationId, actor)
                .map(webMapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List reservations visible to the current actor")
    public Mono<PageResponse<ReservationResponse>> list(
            @RequestParam(required = false) UUID touristId,
            @RequestParam(required = false) UUID providerId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        ReservationFilter filter = new ReservationFilter(
                touristId, providerId, serviceId, status, from, to, page, size);
        return listUseCase.handle(filter, actor)
                .map(result -> PageResponse.from(result, webMapper::toResponse));
    }

    @PatchMapping("/{reservationId}/status")
    @Operation(summary = "Cancel or complete a confirmed reservation")
    public Mono<ReservationResponse> changeStatus(
            @PathVariable UUID reservationId,
            @Valid @RequestBody ChangeReservationStatusRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Actor actor = jwtUserResolver.resolve(jwt);
        return changeStatusUseCase.handle(
                        reservationId,
                        request.status(),
                        request.reason(),
                        actor,
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }
}
