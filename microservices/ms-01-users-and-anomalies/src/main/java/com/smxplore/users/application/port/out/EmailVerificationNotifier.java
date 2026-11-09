package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.Usuario;
import reactor.core.publisher.Mono;

/**
 * Envía el enlace de verificación de correo (MS01-18). Los fallos de envío no deben interrumpir el registro.
 */
public interface EmailVerificationNotifier {

    Mono<Void> notify(Usuario user, String rawToken);
}
