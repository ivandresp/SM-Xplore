package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ReviewGuideUseCase {

    Mono<Void> review(UUID actorId, UUID guideId, ReviewRegistrationCommand command);
}
