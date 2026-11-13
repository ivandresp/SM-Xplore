package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.Usuario;
import reactor.core.publisher.Mono;

/** Avisa por correo al titular cuando una cuenta aprobada se revoca con un motivo. */
public interface AccountRevokedNotifier {

    Mono<Void> notify(Usuario target, String reason);
}
