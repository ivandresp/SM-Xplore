package com.smxplore.users.infrastructure.adapter.out.email;

import com.smxplore.users.application.port.out.AccountInvitationNotifier;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.infrastructure.config.FrontendProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class EmailAccountInvitationNotifier implements AccountInvitationNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailAccountInvitationNotifier.class);

    private final JavaMailSender mailSender;
    private final FrontendProperties frontendProperties;

    public EmailAccountInvitationNotifier(JavaMailSender mailSender, FrontendProperties frontendProperties) {
        this.mailSender = mailSender;
        this.frontendProperties = frontendProperties;
    }

    @Override
    public Mono<Void> notify(Usuario user, String rawToken) {
        return Mono.fromRunnable(() -> sendInvitation(user, rawToken))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void sendInvitation(Usuario user, String rawToken) {
        String link = frontendProperties.baseUrl()
                + frontendProperties.resetPasswordPath()
                + "?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@smxplore.local");
        message.setTo(user.email().value());
        message.setSubject("Activa tu cuenta - SM-Xplore");
        message.setText("""
                Hola %s,

                Un administrador creó tu cuenta en SM-Xplore.
                Abre el siguiente enlace para crear tu contraseña y activar el acceso:

                %s

                Si no esperabas esta invitación, puedes ignorar este mensaje.
                """.formatted(user.fullName(), link));

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo enviar la invitación de cuenta para el usuario {}", user.id(), exception);
        }
    }
}
