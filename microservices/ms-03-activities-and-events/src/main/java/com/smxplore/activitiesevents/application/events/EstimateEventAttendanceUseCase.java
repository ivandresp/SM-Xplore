package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.EstimateEventAttendanceUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class EstimateEventAttendanceUseCase implements EstimateEventAttendanceUseCasePort {

    private final EventRepositoryPort eventRepo;

    @Override
    public Mono<Event> handle(UUID eventId, int expectedAttendance) {
        return eventRepo.findById(eventId)
                .switchIfEmpty(Mono.error(new EventNotFoundException(eventId)))
                .map(event -> estimate(event, expectedAttendance))
                .flatMap(eventRepo::save);
    }

    private Event estimate(Event event, int expectedAttendance) {
        event.estimateAttendance(expectedAttendance);
        return event;
    }
}
