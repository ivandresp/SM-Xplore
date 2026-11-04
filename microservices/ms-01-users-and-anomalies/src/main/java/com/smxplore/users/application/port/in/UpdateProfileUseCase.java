package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.UpdateProfileCommand;
import com.smxplore.users.application.dto.UserView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface UpdateProfileUseCase {

    Mono<UserView> update(UUID userId, UpdateProfileCommand command);
}
