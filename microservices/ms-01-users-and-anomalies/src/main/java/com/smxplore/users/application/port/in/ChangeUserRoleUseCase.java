package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ChangeUserRoleCommand;
import com.smxplore.users.application.dto.UserView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ChangeUserRoleUseCase {

    Mono<UserView> change(UUID actorId, UUID targetUserId, ChangeUserRoleCommand command);
}
