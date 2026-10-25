package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ValidateEventExistsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ValidateEventExistsUseCase implements ValidateEventExistsUseCasePort {

    private final EventRepositoryPort eventRepo;

    @Override
    public Mono<Boolean> handle(UUID eventId) {
        return eventRepo.existsById(eventId);
    }
}
