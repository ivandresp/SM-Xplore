package com.smxplore.users.application.port.out;

import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Registro de auditoría de los accesos al endpoint interno de contactos (MS01-11).
 *
 * <p>Antes de esta corrección el endpoint no dejaba rastro: un servicio comprometido o una clave filtrada podían
 * enumerar correos y teléfonos sin que nadie pudiera saber quién los consultó. Cada intento queda registrado con el
 * servicio de origen y el resultado.
 */
public interface InternalAccessLog {

    Mono<Void> record(String service, UUID targetUserId, String outcome, Instant occurredAt);
}
