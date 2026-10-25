package com.smxplore.activitiesevents.domain.ports.usecases.event;

import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ChangeEventStatusUseCasePort {
    Mono<Event> handle(UUID eventId, EventStatus newStatus);
}
