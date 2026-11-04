package com.smxplore.users.application.port.out;

import com.smxplore.users.application.dto.ComponentAvailability;
import java.util.List;
import reactor.core.publisher.Mono;

public interface SystemAvailabilityProbe {

    Mono<List<ComponentAvailability>> checkComponents();
}
