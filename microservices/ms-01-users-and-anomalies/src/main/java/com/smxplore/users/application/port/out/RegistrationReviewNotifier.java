package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Usuario;
import reactor.core.publisher.Mono;

/**
 * Avisa por correo al titular de un alta revisable cuando una gestora aprueba o rechaza su solicitud.
 */
public interface RegistrationReviewNotifier {

    Mono<Void> notify(Usuario target, RegistrationStatus decision, String reason);
}
