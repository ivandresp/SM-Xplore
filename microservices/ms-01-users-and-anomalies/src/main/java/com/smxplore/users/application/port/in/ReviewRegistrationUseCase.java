package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Aprueba o rechaza el alta de cualquier perfil sometido a revisión (guías y prestadoras de servicios).
 */
public interface ReviewRegistrationUseCase {

    /**
     * Superficie administrativa ({@code /api/v1/admin/users/{id}/…}): un perfil que no se revisa es una petición mal
     * formada (400).
     */
    Mono<Void> review(UUID actorId, UUID targetUserId, ReviewRegistrationCommand command);

    /**
     * Superficie de solicitudes ({@code /api/v1/registrations/{id}/review}): el id designa un alta, así que un id
     * desconocido o de un perfil que no se revisa responde 404.
     */
    Mono<Void> reviewRegistration(UUID actorId, UUID registrationId, ReviewRegistrationCommand command);
}
