package com.smxplore.users.application.port.in;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface AdminResetPasswordUseCase {

    Mono<Void> reset(UUID actorId, UUID targetUserId);
}
