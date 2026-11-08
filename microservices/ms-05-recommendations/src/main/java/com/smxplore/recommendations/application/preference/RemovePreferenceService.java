package com.smxplore.recommendations.application.preference;

import com.smxplore.recommendations.domain.exception.InvalidPreferenceException;
import com.smxplore.recommendations.domain.ports.in.RemovePreferenceUseCase;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import com.smxplore.recommendations.domain.ports.out.RecommendationQuotaPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class RemovePreferenceService implements RemovePreferenceUseCase {

    private final RecommendationGraphPort graphPort;
    private final RecommendationQuotaPort quotaPort;

    @Override
    public Mono<Void> handle(UUID touristId, UUID categoryId) {
        validateCommand(touristId, categoryId);
        quotaPort.checkPreference(touristId);
        return graphPort.deletePreference(touristId, categoryId);
    }

    private void validateCommand(UUID touristId, UUID categoryId) {
        if (touristId == null || categoryId == null) {
            throw new InvalidPreferenceException("touristId and categoryId are required");
        }
    }
}
