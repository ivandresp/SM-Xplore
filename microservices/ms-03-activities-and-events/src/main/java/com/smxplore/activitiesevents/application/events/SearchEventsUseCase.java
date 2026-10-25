package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.SearchEventsUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class SearchEventsUseCase implements SearchEventsUseCasePort {

    private final EventRepositoryPort eventRepo;

    @Override
    public Flux<Event> handle(String name) {
        return eventRepo.searchByName(name);
    }
}
