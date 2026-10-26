package com.smxplore.recommendations.application;

import com.smxplore.recommendations.domain.exception.CategoryNotFoundException;
import com.smxplore.recommendations.domain.exception.InvalidRequestException;
import com.smxplore.recommendations.domain.model.Preference;
import com.smxplore.recommendations.domain.model.PreferenceSource;
import com.smxplore.recommendations.domain.ports.in.ManagePreferencesUseCase;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class PreferenceService implements ManagePreferencesUseCase {

    private final RecommendationGraphPort graphPort;
    private final Clock clock;

    public PreferenceService(RecommendationGraphPort graphPort, Clock clock) {
        this.graphPort = graphPort;
        this.clock = clock;
    }

    @Override
    public Mono<Preference> addPreference(UUID touristId, UUID categoryId, String language) {
        if (touristId == null || categoryId == null) {
            return Mono.error(new InvalidRequestException("touristId and categoryId are required"));
        }

        Instant now = clock.instant();
        return graphPort.upsertPreference(touristId, categoryId, language, now)
                .flatMap(created -> created
                        ? Mono.just(new Preference(touristId, categoryId, 1.0, PreferenceSource.EXPLICIT, now))
                        : Mono.error(new CategoryNotFoundException(categoryId)));
    }

    @Override
    public Mono<Void> removePreference(UUID touristId, UUID categoryId) {
        if (touristId == null || categoryId == null) {
            return Mono.error(new InvalidRequestException("touristId and categoryId are required"));
        }
        return graphPort.deletePreference(touristId, categoryId);
    }
}
