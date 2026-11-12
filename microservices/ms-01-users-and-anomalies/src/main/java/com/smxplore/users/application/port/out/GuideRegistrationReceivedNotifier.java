package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.Usuario;
import reactor.core.publisher.Mono;

/**
 * Confirma por correo que la solicitud pública de registro de un guía se recibió y está en revisión.
 */
public interface GuideRegistrationReceivedNotifier {

    Mono<Void> notify(Usuario guide);
}
