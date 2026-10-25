package com.smxplore.users.identity.infrastructure.adapter.out.email;

import com.smxplore.users.identity.application.port.out.ProviderReviewNotifier;
import com.smxplore.users.identity.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.identity.domain.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailProviderReviewNotifier implements ProviderReviewNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailProviderReviewNotifier.class);

    private final JavaMailSender mailSender;

    public EmailProviderReviewNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void notify(Usuario provider, ProviderRegistrationStatus decision, String reason) {
        boolean approved = decision == ProviderRegistrationStatus.APROBADO;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@smxplore.local");
        message.setTo(provider.email().value());
        message.setSubject((approved ? "Registro aprobado" : "Registro rechazado") + " - SM-Xplore");
        message.setText("""
                Hola %s,

                %s

                %s
                """.formatted(
                        provider.fullName(),
                        approved
                                ? "Tu registro como prestadora de servicios en SM-Xplore fue aprobado. Ya puedes publicar tu oferta turística."
                                : "Tu registro como prestadora de servicios en SM-Xplore fue rechazado.",
                        reason == null || reason.isBlank() ? "" : "Motivo: " + reason.trim()));

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "No se pudo enviar el correo de revisión de la prestadora {}",
                    provider.id().value(),
                    exception);
        }
    }
}
