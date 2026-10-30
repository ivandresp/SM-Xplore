package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.UserView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface GetUserDetailUseCase {

    Mono<UserView> get(UUID userId);
}
