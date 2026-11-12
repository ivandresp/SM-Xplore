package com.smxplore.users.infrastructure.adapter.out.email;

import com.smxplore.users.application.port.out.GuideRegistrationReceivedNotifier;
import com.smxplore.users.domain.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Acusa recibo de la solicitud pública de registro de un guía: el alta quedó guardada y en revisión.
 */
@Component
public class EmailGuideRegistrationReceivedNotifier implements GuideRegistrationReceivedNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailGuideRegistrationReceivedNotifier.class);

    private final JavaMailSender mailSender;

    public EmailGuideRegistrationReceivedNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public Mono<Void> notify(Usuario guide) {
        return Mono.fromRunnable(() -> sendReceived(guide))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void sendReceived(Usuario guide) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@smxplore.local");
        message.setTo(guide.email().value());
        message.setSubject("Solicitud de registro recibida - SM-Xplore");
        message.setText("""
                Hola %s,

                Recibimos tu solicitud de registro como guía en SM-Xplore. Una gestora de destinos la está revisando.

                Te avisaremos a este mismo correo cuando la decisión esté tomada. Mientras tanto la cuenta no puede
                iniciar sesión.
                """.formatted(guide.fullName()));

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo enviar el acuse de la solicitud de registro del guía {}", guide.id(), exception);
        }
    }
}
