package com.smxplore.recommendations.application.preference;

import com.smxplore.recommendations.domain.exception.CategoryNotFoundException;
import com.smxplore.recommendations.domain.exception.InvalidPreferenceException;
import com.smxplore.recommendations.domain.model.preference.Preference;
import com.smxplore.recommendations.domain.model.preference.PreferenceSource;
import com.smxplore.recommendations.domain.ports.in.AddPreferenceUseCase;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class AddPreferenceService implements AddPreferenceUseCase {

    private final RecommendationGraphPort graphPort;
    private final Clock clock;

    @Override
    public Mono<Preference> handle(UUID touristId, UUID categoryId, String language) {
        validateCommand(touristId, categoryId);
        Instant now = clock.instant();
        return addPreference(touristId, categoryId, language, now);
    }

    private Mono<Preference> addPreference(UUID touristId, UUID categoryId, String language, Instant now) {
        return graphPort.upsertPreference(touristId, categoryId, language, now)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(() -> new CategoryNotFoundException(categoryId)))
                .map(ignored -> buildPreference(touristId, categoryId, now));
    }

    private Preference buildPreference(UUID touristId, UUID categoryId, Instant now) {
        Preference preference = Preference.builder()
                .touristId(touristId)
                .categoryId(categoryId)
                .weight(1.0)
                .source(PreferenceSource.EXPLICIT)
                .updatedAt(now)
                .build();
        preference.validate();
        return preference;
    }

    private void validateCommand(UUID touristId, UUID categoryId) {
        if (touristId == null || categoryId == null) {
            throw new InvalidPreferenceException("touristId and categoryId are required");
        }
    }
}
