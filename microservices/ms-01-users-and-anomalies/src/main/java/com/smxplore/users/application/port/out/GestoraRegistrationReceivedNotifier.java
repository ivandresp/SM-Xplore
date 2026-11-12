package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.Usuario;
import reactor.core.publisher.Mono;

/**
 * Confirma por correo que la solicitud pública de registro de una gestora de destinos se recibió y está en revisión.
 */
public interface GestoraRegistrationReceivedNotifier {

    Mono<Void> notify(Usuario gestora);
}
