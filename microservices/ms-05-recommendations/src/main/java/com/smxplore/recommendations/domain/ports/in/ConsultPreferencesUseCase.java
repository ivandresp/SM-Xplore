package com.smxplore.recommendations.domain.ports.in;

import com.smxplore.recommendations.domain.model.preference.Preference;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ConsultPreferencesUseCase {

    Flux<Preference> handle(UUID touristId);
}
