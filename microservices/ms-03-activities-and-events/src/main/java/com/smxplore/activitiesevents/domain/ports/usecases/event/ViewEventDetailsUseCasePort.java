package com.smxplore.activitiesevents.domain.ports.usecases.event;

import com.smxplore.activitiesevents.domain.model.event.Event;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ViewEventDetailsUseCasePort {
    Mono<Event> handle(UUID eventId);
}
