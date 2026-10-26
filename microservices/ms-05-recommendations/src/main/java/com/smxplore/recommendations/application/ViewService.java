package com.smxplore.recommendations.application;

import com.smxplore.recommendations.domain.exception.InvalidRequestException;
import com.smxplore.recommendations.domain.exception.ItemNotFoundException;
import com.smxplore.recommendations.domain.model.ItemType;
import com.smxplore.recommendations.domain.ports.in.RegisterViewUseCase;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class ViewService implements RegisterViewUseCase {

    private final RecommendationGraphPort graphPort;
    private final Clock clock;

    public ViewService(RecommendationGraphPort graphPort, Clock clock) {
        this.graphPort = graphPort;
        this.clock = clock;
    }

    @Override
    public Mono<Void> registerView(UUID touristId, ItemType itemType, UUID itemId, String language) {
        if (touristId == null || itemType == null || itemId == null) {
            return Mono.error(new InvalidRequestException("touristId, itemType and itemId are required"));
        }

        Instant now = clock.instant();
        return graphPort.recordView(touristId, itemType, itemId, language, now)
                .flatMap(found -> found
                        ? Mono.empty()
                        : Mono.error(new ItemNotFoundException(itemType, itemId)));
    }
}
