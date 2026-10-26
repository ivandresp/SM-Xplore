package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.PublicProfileView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface GetPublicProfileUseCase {

    Mono<PublicProfileView> get(UUID userId);
}
