package com.smxplore.activitiesevents.infrastructure.adapters.in.web.controller;

import com.smxplore.activitiesevents.domain.model.availability.Availability;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.ports.usecases.availability.ConsultAvailabilityUseCasePort;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.AvailabilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Availability")
public class AvailabilityController {

    private final ConsultAvailabilityUseCasePort consultAvailabilityUseCase;

    @GetMapping("/events/{eventId}/availability")
    @Operation(summary = "Remaining seats for an event")
    public Mono<AvailabilityResponse> eventAvailability(@PathVariable UUID eventId) {
        return consultAvailabilityUseCase.handle(eventId, ReservationItemType.EVENT).map(this::toResponse);
    }

    @GetMapping("/activities/{activityId}/availability")
    @Operation(summary = "Remaining seats for an activity")
    public Mono<AvailabilityResponse> activityAvailability(@PathVariable UUID activityId) {
        return consultAvailabilityUseCase.handle(activityId, ReservationItemType.ACTIVITY).map(this::toResponse);
    }

    private AvailabilityResponse toResponse(Availability availability) {
        return new AvailabilityResponse(availability.itemId(), availability.itemType(),
                availability.maxCapacity(), availability.reserved(), availability.available());
    }
}
