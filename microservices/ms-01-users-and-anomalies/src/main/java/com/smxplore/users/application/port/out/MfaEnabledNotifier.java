package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.Usuario;
import reactor.core.publisher.Mono;

/**
 * Avisa al usuario cuando se activa un segundo factor en su cuenta. El envío es best-effort: un fallo de correo no debe
 * revertir la activación.
 */
public interface MfaEnabledNotifier {

    Mono<Void> notify(Usuario user);
}
