package com.smxplore.recommendations.application.preference;

import com.smxplore.recommendations.domain.exception.InvalidPreferenceException;
import com.smxplore.recommendations.domain.model.preference.Preference;
import com.smxplore.recommendations.domain.ports.in.ReplacePreferencesUseCase;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import com.smxplore.recommendations.domain.ports.out.RecommendationQuotaPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MS05-11: bulk replacement of the explicit preferences of a tourist.
 *
 * <p>A single graph write deletes every explicit edge and re-creates the requested ones, so a
 * client boots with one round trip instead of N POSTs. Unknown categories are rejected before any
 * edge is written (the adapter validates all ids inside the same transaction).</p>
 */
@RequiredArgsConstructor
public class ReplacePreferencesService implements ReplacePreferencesUseCase {

    /** Same cap the onboarding UI uses; it keeps one request from writing an unbounded batch. */
    static final int MAX_CATEGORIES = 20;

    private final RecommendationGraphPort graphPort;
    private final RecommendationQuotaPort quotaPort;
    private final Clock clock;

    @Override
    public Mono<List<Preference>> handle(UUID touristId, List<UUID> categoryIds, String language) {
        validateCommand(touristId, categoryIds);
        quotaPort.checkPreference(touristId);
        return graphPort.replacePreferences(touristId, List.copyOf(categoryIds), language, clock.instant());
    }

    private void validateCommand(UUID touristId, List<UUID> categoryIds) {
        if (touristId == null) {
            throw new InvalidPreferenceException("touristId is required");
        }
        if (categoryIds == null) {
            throw new InvalidPreferenceException("categoryIds is required");
        }
        if (categoryIds.size() > MAX_CATEGORIES) {
            throw new InvalidPreferenceException(
                    "categoryIds cannot contain more than " + MAX_CATEGORIES + " categories");
        }
        if (categoryIds.stream().anyMatch(Objects::isNull)) {
            throw new InvalidPreferenceException("categoryIds cannot contain null values");
        }
        if (new HashSet<>(categoryIds).size() != categoryIds.size()) {
            throw new InvalidPreferenceException("categoryIds cannot contain duplicates");
        }
    }
}
