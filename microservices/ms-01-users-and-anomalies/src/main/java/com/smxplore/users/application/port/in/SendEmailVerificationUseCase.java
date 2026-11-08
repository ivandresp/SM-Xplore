package com.smxplore.users.application.port.in;

import java.util.UUID;
import reactor.core.publisher.Mono;

/**
 * Reenvía el enlace de verificación de correo de la cuenta autenticada (MS01-18).
 */
public interface SendEmailVerificationUseCase {

    Mono<Void> send(UUID userId);
}
