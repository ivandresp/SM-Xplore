package com.smxplore.users.infrastructure.adapter.out.email;

import com.smxplore.users.application.port.out.MfaEnabledNotifier;
import com.smxplore.users.domain.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Envía un correo de aviso cuando se activa la autenticación multifactor. Un fallo de envío se registra pero no revierte
 * la activación.
 */
@Component
public final class EmailMfaEnabledNotifier implements MfaEnabledNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailMfaEnabledNotifier.class);

    private final JavaMailSender mailSender;

    public EmailMfaEnabledNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public Mono<Void> notify(Usuario user) {
        return Mono.fromRunnable(() -> send(user))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void send(Usuario user) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("no-reply@smxplore.local");
            message.setTo(user.email().value());
            message.setSubject("Autenticación multifactor activada - SM-Xplore");
            message.setText("""
                    Hola %s:

                    La autenticación multifactor quedó activada en tu cuenta de SM-Xplore.
                    A partir de ahora necesitarás un código de tu aplicación autenticadora para iniciar sesión.

                    Si no fuiste tú, cambia tu contraseña de inmediato y contacta al equipo de soporte.
                    """.formatted(user.fullName()));
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo enviar el aviso de activación de MFA para el usuario {}", user.id(), exception);
        }
    }
}
