package com.smxplore.activitiesevents.domain.ports.usecases.event;

import com.smxplore.activitiesevents.domain.model.event.Event;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EstimateEventAttendanceUseCasePort {
    Mono<Event> handle(UUID eventId, int expectedAttendance);
}
