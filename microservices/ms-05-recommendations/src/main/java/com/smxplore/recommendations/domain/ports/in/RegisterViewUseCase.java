package com.smxplore.recommendations.domain.ports.in;

import com.smxplore.recommendations.domain.model.ItemType;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface RegisterViewUseCase {

    Mono<Void> registerView(UUID touristId, ItemType itemType, UUID itemId, String language);
}
