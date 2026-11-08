package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.Email;
import java.time.Duration;
import reactor.core.publisher.Mono;

/**
 * Avisa al dueño de la cuenta cuando el bloqueo por intentos fallidos se activa.
 *
 * <p>MS01-05: el contador de intentos es compartido y persiste entre instancias, así que cualquiera puede bloquear la
 * cuenta de otra persona durante 15 minutos. El aviso por correo permite que la persona afectada se entere y use el
 * flujo de recuperación de contraseña, que además limpia el bloqueo.
 */
public interface AccountLockedNotifier {

    Mono<Void> notify(Email email, Duration remaining);
}
