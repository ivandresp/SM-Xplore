package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.CreateInternalUserCommand;
import com.smxplore.users.application.dto.UserView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface CreateInternalUserUseCase {

    Mono<UserView> create(UUID actorId, CreateInternalUserCommand command);
}
