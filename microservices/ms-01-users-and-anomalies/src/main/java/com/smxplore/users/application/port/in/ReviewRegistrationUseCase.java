package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Aprueba o rechaza el alta de cualquier perfil sometido a revisión (guías y prestadoras de servicios).
 */
public interface ReviewRegistrationUseCase {

    Mono<Void> review(UUID actorId, UUID targetUserId, ReviewRegistrationCommand command);
}
