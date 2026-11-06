package com.smxplore.activitiesevents.domain.ports.usecases.activity;

import com.smxplore.activitiesevents.domain.model.activity.Activity;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

public interface UpdateActivityUseCasePort {
    Mono<Activity> handle(UUID activityId, String name, String category, String description, BigDecimal price);
}
