package com.smxplore.sitesandservices.infrastructure.adapters.out.mail;

import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationContact;
import com.smxplore.sitesandservices.domain.model.notification.NotificationType;
import com.smxplore.sitesandservices.domain.ports.out.NotificationContactRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.config.NotificationEmailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNotificationDeliveryTest {

    private final UUID recipientId = UUID.randomUUID();
    private final InMemoryContactRepository contacts = new InMemoryContactRepository();
    private final RecordingMailSender mailSender = new RecordingMailSender();
    private final NotificationEmailProperties properties = new NotificationEmailProperties();

    @Test
    void sendsAnEmailWithTheTitleAndMessage() {
        properties.setEnabled(true);
        properties.setFrom("no-reply@smxplore.test");
        properties.setSubjectPrefix("[SM-Xplore] ");
        contacts.contact = Optional.of(contact("tourist@example.com"));

        StepVerifier.create(delivery().deliver(notification())).verifyComplete();

        assertThat(mailSender.sent).hasSize(1);
        SimpleMailMessage message = mailSender.sent.getFirst();
        assertThat(message.getFrom()).isEqualTo("no-reply@smxplore.test");
        assertThat(message.getTo()).containsExactly("tourist@example.com");
        assertThat(message.getSubject()).isEqualTo("[SM-Xplore] Reserva confirmada");
        assertThat(message.getText()).contains("Gracias por reservar");
        assertThat(message.getText()).contains("bandeja de notificaciones");
    }

    @Test
    void skipsDeliveryWhenTheChannelIsDisabled() {
        properties.setEnabled(false);
        contacts.contact = Optional.of(contact("tourist@example.com"));

        StepVerifier.create(delivery().deliver(notification())).verifyComplete();

        assertThat(mailSender.sent).isEmpty();
    }

    @Test
    void skipsDeliveryWhenTheRecipientHasNoContact() {
        properties.setEnabled(true);
        contacts.contact = Optional.empty();

        StepVerifier.create(delivery().deliver(notification())).verifyComplete();

        assertThat(mailSender.sent).isEmpty();
    }

    @Test
    void propagatesTransportFailuresSoTheInboxStaysAuthoritative() {
        properties.setEnabled(true);
        contacts.contact = Optional.of(contact("tourist@example.com"));
        mailSender.failure = new MailSendException("SMTP is down");

        StepVerifier.create(delivery().deliver(notification()))
                .expectError(MailSendException.class)
                .verify();
    }

    private EmailNotificationDelivery delivery() {
        return new EmailNotificationDelivery(contacts, mailSender, properties);
    }

    private NotificationContact contact(String email) {
        return NotificationContact.builder()
                .userId(recipientId)
                .email(email)
                .updatedAt(Instant.parse("2026-11-03T12:00:00Z"))
                .build();
    }

    private Notification notification() {
        return Notification.builder()
                .notificationId(UUID.randomUUID())
                .recipientId(recipientId)
                .recipientRole(UserRole.TURISTA)
                .reservationId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .type(NotificationType.RESERVATION_CONFIRMED)
                .title("Reserva confirmada")
                .message("Gracias por reservar. Tu reserva quedó confirmada.")
                .createdAt(Instant.parse("2026-11-03T12:00:00Z"))
                .build();
    }

    private static final class InMemoryContactRepository implements NotificationContactRepositoryPort {

        private Optional<NotificationContact> contact = Optional.empty();

        @Override
        public Mono<NotificationContact> upsert(NotificationContact value) {
            contact = Optional.of(value);
            return Mono.just(value);
        }

        @Override
        public Mono<Optional<NotificationContact>> findByUserId(UUID userId) {
            return Mono.just(contact);
        }

        @Override
        public Mono<Void> deleteByUserId(UUID userId) {
            contact = Optional.empty();
            return Mono.empty();
        }
    }

    private static final class RecordingMailSender implements JavaMailSender {

        private final List<SimpleMailMessage> sent = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void send(SimpleMailMessage simpleMessage) {
            if (failure != null) {
                throw failure;
            }
            sent.add(simpleMessage);
        }

        @Override
        public void send(SimpleMailMessage... simpleMessages) {
            for (SimpleMailMessage message : simpleMessages) {
                send(message);
            }
        }

        @Override
        public void send(MimeMessage... mimeMessages) {
            throw new UnsupportedOperationException("not used by the notification adapter");
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            throw new UnsupportedOperationException("not used by the notification adapter");
        }

        @Override
        public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }

        @Override
        public MimeMessage createMimeMessage(InputStream contentStream) {
            throw new UnsupportedOperationException("not used by the notification adapter");
        }
    }
}
