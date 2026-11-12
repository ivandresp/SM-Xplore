package com.smxplore.recommendations.domain.ports.in;

import com.smxplore.recommendations.domain.model.preference.Preference;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Replaces the whole set of explicit preferences of a tourist in one operation (MS05-11).
 * Inferred preferences are left untouched.
 */
public interface ReplacePreferencesUseCase {

    Mono<List<Preference>> handle(UUID touristId, List<UUID> categoryIds, String language);
}
