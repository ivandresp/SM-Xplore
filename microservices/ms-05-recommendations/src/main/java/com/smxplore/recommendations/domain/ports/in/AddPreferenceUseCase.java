package com.smxplore.recommendations.domain.ports.in;

import com.smxplore.recommendations.domain.model.preference.Preference;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AddPreferenceUseCase {

    Mono<Preference> handle(UUID touristId, UUID categoryId, String language);
}
