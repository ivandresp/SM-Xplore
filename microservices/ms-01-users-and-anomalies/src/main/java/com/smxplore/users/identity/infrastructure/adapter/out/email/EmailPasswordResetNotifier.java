package com.smxplore.users.identity.infrastructure.adapter.out.email;

import com.smxplore.users.identity.application.port.out.PasswordResetNotifier;
import com.smxplore.users.identity.domain.model.User;
import com.smxplore.users.shared.infrastructure.config.FrontendProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailPasswordResetNotifier implements PasswordResetNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailPasswordResetNotifier.class);

    private final JavaMailSender mailSender;
    private final FrontendProperties frontendProperties;

    public EmailPasswordResetNotifier(JavaMailSender mailSender, FrontendProperties frontendProperties) {
        this.mailSender = mailSender;
        this.frontendProperties = frontendProperties;
    }

    @Override
    public void notify(User user, String rawToken) {
        String link = frontendProperties.baseUrl()
                + frontendProperties.resetPasswordPath()
                + "?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@smxplore.local");
        message.setTo(user.email().value());
        message.setSubject("Recuperación de contraseña - SM-Xplore");
        message.setText("""
                Hola %s,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta en SM-Xplore.
                Abre el siguiente enlace para crear una nueva contraseña:

                %s

                Si no realizaste esta solicitud, puedes ignorar este mensaje.
                """.formatted(user.fullName().value(), link));

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "No se pudo enviar el correo de recuperación para el usuario {}",
                    user.id().value(),
                    exception);
        }
    }
}
