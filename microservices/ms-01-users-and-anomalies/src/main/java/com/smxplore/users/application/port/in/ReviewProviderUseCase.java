package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ReviewProviderCommand;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ReviewProviderUseCase {

    Mono<Void> review(UUID actorId, UUID targetUserId, ReviewProviderCommand command);
}
