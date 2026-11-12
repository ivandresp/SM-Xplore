package com.smxplore.users.infrastructure.adapter.out.email;

import com.smxplore.users.application.port.out.RegistrationReviewNotifier;
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

/**
 * Avisa al titular del alta revisable (guía o prestadora de servicios) de la decisión de la gestora.
 *
 * <p>El envío nunca rompe la revisión: si el SMTP falla se registra el error y la decisión ya quedó guardada.
 */
@Component
public class EmailRegistrationReviewNotifier implements RegistrationReviewNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailRegistrationReviewNotifier.class);

    private final JavaMailSender mailSender;

    public EmailRegistrationReviewNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public Mono<Void> notify(Usuario target, RegistrationStatus decision, String reason) {
        return Mono.fromRunnable(() -> sendReview(target, decision, reason))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void sendReview(Usuario target, RegistrationStatus decision, String reason) {
        boolean approved = decision == RegistrationStatus.APROBADO;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@smxplore.local");
        message.setTo(target.email().value());
        message.setSubject((approved ? "Registro aprobado" : "Registro rechazado") + " - SM-Xplore");
        message.setText("""
                Hola %s,

                %s

                %s
                """.formatted(
                        target.fullName(),
                        decisionText(target, approved),
                        reason == null || reason.isBlank() ? "" : "Motivo: " + reason.trim()));

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo enviar el correo de revisión del alta de {}", target.id(), exception);
        }
    }

    private static String decisionText(Usuario target, boolean approved) {
        boolean guide = target instanceof Guia;
        if (approved) {
            return guide
                    ? "Tu registro como guía en SM-Xplore fue aprobado. Ya puedes iniciar sesión y ofrecer tus servicios."
                    : "Tu registro como prestadora de servicios en SM-Xplore fue aprobado. Ya puedes publicar tu oferta turística.";
        }
        return guide
                ? "Tu solicitud de registro como guía en SM-Xplore fue rechazada, así que la cuenta no podrá iniciar sesión."
                : "Tu registro como prestadora de servicios en SM-Xplore fue rechazado.";
    }
}
