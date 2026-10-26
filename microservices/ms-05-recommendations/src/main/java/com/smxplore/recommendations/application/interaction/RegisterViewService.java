package com.smxplore.recommendations.application.interaction;

import com.smxplore.recommendations.domain.exception.InvalidRequestException;
import com.smxplore.recommendations.domain.exception.ItemNotFoundException;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.ports.in.RegisterViewUseCase;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class RegisterViewService implements RegisterViewUseCase {

    private final RecommendationGraphPort graphPort;
    private final Clock clock;

    @Override
    public Mono<Void> handle(UUID touristId, ItemType itemType, UUID itemId, String language) {
        validateCommand(touristId, itemType, itemId);
        Instant now = clock.instant();
        return registerView(touristId, itemType, itemId, language, now);
    }

    private Mono<Void> registerView(
            UUID touristId,
            ItemType itemType,
            UUID itemId,
            String language,
            Instant now
    ) {
        return graphPort.recordView(touristId, itemType, itemId, language, now)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new ItemNotFoundException(itemType, itemId)))
                .then();
    }

    private void validateCommand(UUID touristId, ItemType itemType, UUID itemId) {
        if (touristId == null || itemType == null || itemId == null) {
            throw new InvalidRequestException("touristId, itemType and itemId are required");
        }
    }
}
