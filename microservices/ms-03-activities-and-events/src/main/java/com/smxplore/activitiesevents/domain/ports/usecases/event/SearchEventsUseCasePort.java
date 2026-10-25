package com.smxplore.activitiesevents.domain.ports.usecases.event;

import com.smxplore.activitiesevents.domain.model.event.Event;

import reactor.core.publisher.Flux;

public interface SearchEventsUseCasePort {
    Flux<Event> handle(String name);
}
