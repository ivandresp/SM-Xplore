package com.smxplore.sitesandservices.infrastructure.adapters.out.mail;

import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationContact;
import com.smxplore.sitesandservices.domain.ports.out.NotificationContactRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.NotificationDeliveryPort;
import com.smxplore.sitesandservices.infrastructure.config.NotificationEmailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationDelivery implements NotificationDeliveryPort {

    private final NotificationContactRepositoryPort contactRepository;
    private final JavaMailSender mailSender;
    private final NotificationEmailProperties properties;

    @Override
    public Mono<Void> deliver(Notification notification) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }
        return contactRepository.findByUserId(notification.recipientId())
                .flatMap(contact -> contact
                        .map(value -> send(notification, value))
                        .orElseGet(() -> withoutContact(notification)));
    }

    private Mono<Void> send(Notification notification, NotificationContact contact) {
        return Mono.fromRunnable(() -> mailSender.send(message(notification, contact)))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<Void> withoutContact(Notification notification) {
        log.debug(
                "Notification {} has no email contact for recipient {}; only the inbox was written",
                notification.notificationId(),
                notification.recipientId());
        return Mono.empty();
    }

    private SimpleMailMessage message(Notification notification, NotificationContact contact) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom());
        message.setTo(contact.email());
        message.setSubject(properties.getSubjectPrefix() + notification.title());
        message.setText(notification.message() + "\n\nRevisa la bandeja de notificaciones en SM-Xplore.");
        return message;
    }
}
