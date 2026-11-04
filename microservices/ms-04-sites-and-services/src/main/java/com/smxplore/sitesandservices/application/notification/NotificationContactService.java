package com.smxplore.sitesandservices.application.notification;

import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.notification.NotificationContact;
import com.smxplore.sitesandservices.domain.ports.out.NotificationContactRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class NotificationContactService {

    private final NotificationContactRepositoryPort contactRepository;
    private final CatalogAccess access;
    private final Clock clock;

    public Mono<NotificationContact> upsert(UUID userId, String email, Actor actor) {
        return access.requireAdmin(actor)
                .then(Mono.fromSupplier(() -> {
                    NotificationContact contact = NotificationContact.builder()
                            .userId(userId)
                            .email(email == null ? null : email.trim())
                            .updatedAt(Instant.now(clock))
                            .build();
                    contact.validate();
                    return contact;
                }))
                .flatMap(contactRepository::upsert);
    }

    public Mono<Void> delete(UUID userId, Actor actor) {
        return access.requireAdmin(actor)
                .then(contactRepository.deleteByUserId(userId));
    }
}
