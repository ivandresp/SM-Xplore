package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ComponentAvailability;
import com.smxplore.users.application.dto.SystemAvailability;
import com.smxplore.users.application.port.in.GetSystemAvailabilityUseCase;
import com.smxplore.users.application.port.out.SystemAvailabilityProbe;
import java.time.Clock;
import reactor.core.publisher.Mono;

public final class GetSystemAvailabilityService implements GetSystemAvailabilityUseCase {

    private final SystemAvailabilityProbe probe;
    private final Clock clock;

    public GetSystemAvailabilityService(SystemAvailabilityProbe probe, Clock clock) {
        this.probe = probe;
        this.clock = clock;
    }

    @Override
    public Mono<SystemAvailability> get() {
        return probe.checkComponents()
                .map(components -> new SystemAvailability(
                        components.stream().allMatch(ComponentAvailability::available), clock.instant(), components));
    }
}
