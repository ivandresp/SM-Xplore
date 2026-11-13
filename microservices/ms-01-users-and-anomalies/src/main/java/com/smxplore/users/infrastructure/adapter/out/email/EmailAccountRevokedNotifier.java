package com.smxplore.users.infrastructure.adapter.out.email;

import com.smxplore.users.application.port.out.AccountRevokedNotifier;
import com.smxplore.users.domain.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Avisa de una revocación con el motivo. Un fallo de SMTP no revierte la revocación: se registra y sigue.
 */
@Component
public class EmailAccountRevokedNotifier implements AccountRevokedNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailAccountRevokedNotifier.class);

    private final JavaMailSender mailSender;

    public EmailAccountRevokedNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public Mono<Void> notify(Usuario target, String reason) {
        return Mono.fromRunnable(() -> send(target, reason))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void send(Usuario target, String reason) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@smxplore.local");
        message.setTo(target.email().value());
        message.setSubject("Cuenta revocada - SM-Xplore");
        message.setText("""
                Hola %s,

                Tu cuenta en SM-Xplore fue revocada y ya no puede operar.

                Motivo: %s

                Si crees que es un error, responde a este correo.
                """.formatted(target.fullName(), reason == null ? "" : reason.trim()));

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo enviar el correo de revocación de {}", target.id(), exception);
        }
    }
}
