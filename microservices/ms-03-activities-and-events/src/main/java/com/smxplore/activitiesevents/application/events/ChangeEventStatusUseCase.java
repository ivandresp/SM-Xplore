package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.exceptions.event.EventNotFoundException;
import com.smxplore.activitiesevents.application.outbox.OutboxPublisher;
import com.smxplore.activitiesevents.application.outbox.EventChangedEventFactory;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ChangeEventStatusUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ChangeEventStatusUseCase implements ChangeEventStatusUseCasePort {

    private final EventRepositoryPort eventRepo;
    private final EventChangedEventFactory eventEvents;
    private final OutboxPublisher publisher;

    @Override
    public Mono<Event> handle(UUID eventId, EventStatus newStatus) {
        return eventRepo.findById(eventId)
                .switchIfEmpty(Mono.error(new EventNotFoundException(eventId)))
                .map(event -> changeStatus(event, newStatus))
                .flatMap(eventRepo::save)
                .flatMap(saved -> publisher.publish(saved, "activities.event.changed", saved.getId(),
                        eventEvents.eventChanged(saved)));
    }

    private Event changeStatus(Event event, EventStatus newStatus) {
        event.changeStatus(newStatus);
        return event;
    }
}