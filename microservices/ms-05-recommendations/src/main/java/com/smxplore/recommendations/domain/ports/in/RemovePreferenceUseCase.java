package com.smxplore.recommendations.domain.ports.in;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RemovePreferenceUseCase {

    Mono<Void> handle(UUID touristId, UUID categoryId);
}
