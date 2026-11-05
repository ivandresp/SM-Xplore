package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.application.outbox.BestEffortPublisher;
import com.smxplore.activitiesevents.application.outbox.EventChangedEventFactory;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.RegisterEventUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RegisterEventUseCase implements RegisterEventUseCasePort {

    private final EventRepositoryPort eventRepo;
    private final EventChangedEventFactory eventEvents;
    private final BestEffortPublisher publisher;

    @Override
    public Mono<Event> handle(Event event) {
        return Mono.justOrEmpty(event)
                .switchIfEmpty(Mono.error(new InvalidEventRequestException("Event cannot be null")))
                .map(this::validate)
                .flatMap(eventRepo::save)
                .flatMap(saved -> publisher.publish(saved, "activities.event.changed", saved.getId(),
                        eventEvents.eventChanged(saved)));
    }

    private Event validate(Event event) {
        event.validate();
        return event;
    }
}