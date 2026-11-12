package com.smxplore.users.infrastructure.adapter.out.email;

import com.smxplore.users.application.port.out.GestoraRegistrationReceivedNotifier;
import com.smxplore.users.domain.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Acusa recibo de la solicitud pública de registro de una gestora de destinos: el alta quedó guardada y en revisión.
 */
@Component
public class EmailGestoraRegistrationReceivedNotifier implements GestoraRegistrationReceivedNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailGestoraRegistrationReceivedNotifier.class);

    private final JavaMailSender mailSender;

    public EmailGestoraRegistrationReceivedNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public Mono<Void> notify(Usuario gestora) {
        return Mono.fromRunnable(() -> sendReceived(gestora))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void sendReceived(Usuario gestora) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@smxplore.local");
        message.setTo(gestora.email().value());
        message.setSubject("Solicitud de registro recibida - SM-Xplore");
        message.setText("""
                Hola %s,

                Recibimos tu solicitud de registro como gestora de destinos en SM-Xplore. Un administrador la está revisando.

                Te avisaremos a este mismo correo cuando la decisión esté tomada. Mientras tanto la cuenta no puede
                iniciar sesión.
                """.formatted(gestora.fullName()));

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "No se pudo enviar el acuse de la solicitud de registro de la gestora {}", gestora.id(), exception);
        }
    }
}
