package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.SystemAvailability;
import reactor.core.publisher.Mono;

public interface GetSystemAvailabilityUseCase {

    Mono<SystemAvailability> get();
}
