package com.smxplore.users.infrastructure.adapter.out.email;

import com.smxplore.users.application.port.out.AccountLockedNotifier;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.infrastructure.config.FrontendProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Aviso de bloqueo temporal de cuenta (MS01-05). Es un envío best-effort: si el correo falla se registra el error pero
 * el bloqueo ya está aplicado y el flujo de login no cambia.
 */
@Component
public class EmailAccountLockedNotifier implements AccountLockedNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailAccountLockedNotifier.class);

    private static final String FROM = "no-reply@smxplore.local";

    private final JavaMailSender mailSender;
    private final FrontendProperties frontend;

    public EmailAccountLockedNotifier(JavaMailSender mailSender, FrontendProperties frontend) {
        this.mailSender = mailSender;
        this.frontend = frontend;
    }

    @Override
    public Mono<Void> notify(Email email, Duration remaining) {
        return Mono.fromRunnable(() -> send(email, remaining))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void send(Email email, Duration remaining) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(FROM);
        message.setTo(email.value());
        message.setSubject("Bloqueo temporal de tu cuenta - SM-Xplore");
        message.setText(
                """
                Detectamos varios intentos de inicio de sesión fallidos en tu cuenta de SM-Xplore y la bloqueamos \
                durante %d minutos por seguridad.

                Si fuiste tú, espera unos minutos e inténtalo de nuevo. Si no reconoces estos intentos, restablece tu \
                contraseña aquí: %s%s

                Restablecer la contraseña también elimina el bloqueo temporal.
                """.formatted(Math.max(1, remaining.toMinutes()), frontend.baseUrl(), frontend.resetPasswordPath()));
        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo enviar el aviso de bloqueo de cuenta para {}", email.value(), exception);
        }
    }
}
