package com.smxplore.users.application.port.in;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface DeleteUserUseCase {

    Mono<Void> delete(UUID actorId, UUID targetUserId);
}
