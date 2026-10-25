package com.smxplore.activitiesevents.domain.ports.usecases.event;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ValidateEventExistsUseCasePort {

    Mono<Boolean> handle(UUID eventId);
}
