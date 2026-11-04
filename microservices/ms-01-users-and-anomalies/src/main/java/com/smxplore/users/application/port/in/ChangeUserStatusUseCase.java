package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ChangeUserStatusCommand;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ChangeUserStatusUseCase {

    Mono<Void> change(UUID actorId, UUID targetUserId, ChangeUserStatusCommand command);
}
