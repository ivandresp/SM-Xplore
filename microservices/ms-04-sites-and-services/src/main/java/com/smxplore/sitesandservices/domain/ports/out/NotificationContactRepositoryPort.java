package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.notification.NotificationContact;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

public interface NotificationContactRepositoryPort {

    Mono<NotificationContact> upsert(NotificationContact contact);

    Mono<Optional<NotificationContact>> findByUserId(UUID userId);

    Mono<Void> deleteByUserId(UUID userId);
}
