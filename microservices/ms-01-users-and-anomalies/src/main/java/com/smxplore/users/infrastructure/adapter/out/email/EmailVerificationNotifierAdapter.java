package com.smxplore.users.infrastructure.adapter.out.email;

import com.smxplore.users.application.port.out.EmailVerificationNotifier;
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

/**
 * Envía el enlace de verificación de correo (MS01-18). Igual que el resto de notificadores, un fallo de SMTP se
 * registra pero no rompe el registro de la cuenta: el usuario puede pedir el reenvío desde su perfil.
 */
@Component
public class EmailVerificationNotifierAdapter implements EmailVerificationNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationNotifierAdapter.class);

    private final JavaMailSender mailSender;
    private final FrontendProperties frontendProperties;

    public EmailVerificationNotifierAdapter(JavaMailSender mailSender, FrontendProperties frontendProperties) {
        this.mailSender = mailSender;
        this.frontendProperties = frontendProperties;
    }

    @Override
    public Mono<Void> notify(Usuario user, String rawToken) {
        return Mono.fromRunnable(() -> sendVerification(user, rawToken))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void sendVerification(Usuario user, String rawToken) {
        String link = frontendProperties.baseUrl()
                + frontendProperties.verifyEmailPath()
                + "?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("no-reply@smxplore.local");
        message.setTo(user.email().value());
        message.setSubject("Verifica tu correo - SM-Xplore");
        message.setText("""
                Hola %s,

                Confirma que este correo pertenece a tu cuenta de SM-Xplore abriendo el siguiente enlace:

                %s

                Si no creaste esta cuenta, puedes ignorar este mensaje.
                """.formatted(user.fullName(), link));

        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo enviar el correo de verificación para el usuario {}", user.id(), exception);
        }
    }
}
