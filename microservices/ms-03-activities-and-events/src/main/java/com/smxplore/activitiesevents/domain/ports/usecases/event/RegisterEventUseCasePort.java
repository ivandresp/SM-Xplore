package com.smxplore.activitiesevents.domain.ports.usecases.event;

import com.smxplore.activitiesevents.domain.model.event.Event;

import reactor.core.publisher.Mono;

public interface RegisterEventUseCasePort {
    Mono<Event> handle(Event event);
}
