package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.RegisterEventUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RegisterEventUseCase implements RegisterEventUseCasePort {

    private final EventRepositoryPort eventRepo;

    @Override
    public Mono<Event> handle(Event event) {
        return Mono.justOrEmpty(event)
                .switchIfEmpty(Mono.error(new InvalidEventRequestException("Event cannot be null")))
                .map(this::validate)
                .flatMap(eventRepo::save);
    }

    private Event validate(Event event) {
        event.validate();
        return event;
    }
}
