package com.smxplore.activitiesevents.domain.ports.usecases.event;

import com.smxplore.activitiesevents.domain.model.event.Event;

import reactor.core.publisher.Flux;

public interface ConsultEventsUseCasePort {
    Flux<Event> handle();
}
