package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Usuario;
import reactor.core.publisher.Mono;

/**
 * Notifica al guía o a la gestora de destinos la decisión tomada sobre su registro.
 */
public interface RegistrationReviewNotifier {

    Mono<Void> notify(Usuario user, RegistrationStatus decision, String reason);
}
