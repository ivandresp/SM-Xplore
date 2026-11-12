package com.smxplore.users.infrastructure.adapter.out.email;

import com.smxplore.users.application.port.out.RegistrationReviewNotifier;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class EmailRegistrationReviewNotifier implements RegistrationReviewNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailRegistrationReviewNotifier.class);

    private final JavaMailSender mailSender;

    public EmailRegistrationReviewNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public Mono<Void> notify(Usuario user, RegistrationStatus decision, String reason) {
        return Mono.fromRunnable(() -> sendReview(user, decision, reason))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void sendReview(Usuario user, RegistrationStatus decision, String reason) {
        boolean approved = decision == RegistrationStatus.APROBADO;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@smxplore.local");
        message.setTo(user.email().value());
        message.setSubject((approved ? "Registro aprobado" : "Registro rechazado") + " - SM-Xplore");
        message.setText("""
                Hola %s,

                %s

                %s
                """.formatted(user.fullName(), bodyFor(user, approved), reasonText(reason)));

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo enviar el correo de revisión del registro {}", user.id(), exception);
        }
    }

    private static String bodyFor(Usuario user, boolean approved) {
        return switch (user) {
            case Guia guide ->
                approved
                        ? "Tu registro como guía en SM-Xplore fue aprobado. Ya puedes recibir solicitudes de turistas."
                        : "Tu registro como guía en SM-Xplore fue rechazado.";
            case GestoraDestinos gestora ->
                approved
                        ? "Tu registro como gestora de destinos en SM-Xplore fue aprobado. Ya puedes revisar las guías."
                        : "Tu registro como gestora de destinos en SM-Xplore fue rechazado.";
            default -> approved ? "Tu registro en SM-Xplore fue aprobado." : "Tu registro en SM-Xplore fue rechazado.";
        };
    }

    private static String reasonText(String reason) {
        return reason == null || reason.isBlank() ? "" : "Motivo: " + reason.trim();
    }
}
