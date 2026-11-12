package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ReviewGestoraUseCase {

    Mono<Void> review(UUID actorId, UUID gestoraId, ReviewRegistrationCommand command);
}
