package com.smxplore.recommendations.domain.ports.in;

import com.smxplore.recommendations.domain.model.Preference;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ManagePreferencesUseCase {

    Mono<Preference> addPreference(UUID touristId, UUID categoryId, String language);

    Mono<Void> removePreference(UUID touristId, UUID categoryId);
}
