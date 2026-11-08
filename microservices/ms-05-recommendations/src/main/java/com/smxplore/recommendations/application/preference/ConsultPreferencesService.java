package com.smxplore.recommendations.application.preference;

import com.smxplore.recommendations.domain.exception.InvalidPreferenceException;
import com.smxplore.recommendations.domain.model.preference.Preference;
import com.smxplore.recommendations.domain.ports.in.ConsultPreferencesUseCase;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultPreferencesService implements ConsultPreferencesUseCase {

    private final RecommendationGraphPort graphPort;

    @Override
    public Flux<Preference> handle(UUID touristId) {
        if (touristId == null) {
            throw new InvalidPreferenceException("touristId is required");
        }
        return graphPort.findPreferences(touristId);
    }
}
