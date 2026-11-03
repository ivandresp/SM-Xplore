package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.model.notification.NotificationContact;
import com.smxplore.sitesandservices.domain.ports.out.NotificationContactRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.NotificationContactDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MongoNotificationContactRepositoryAdapter implements NotificationContactRepositoryPort {

    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<NotificationContact> upsert(NotificationContact contact) {
        Query query = Query.query(Criteria.where("_id").is(contact.userId().toString()));
        Update update = new Update()
                .set("email", contact.email())
                .set("updatedAt", contact.updatedAt());
        return mongoTemplate.upsert(query, update, NotificationContactDocument.class)
                .thenReturn(contact);
    }

    @Override
    public Mono<Optional<NotificationContact>> findByUserId(UUID userId) {
        return mongoTemplate.findById(userId.toString(), NotificationContactDocument.class)
                .map(document -> Optional.of(toDomain(document)))
                .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<Void> deleteByUserId(UUID userId) {
        Query query = Query.query(Criteria.where("_id").is(userId.toString()));
        return mongoTemplate.remove(query, NotificationContactDocument.class).then();
    }

    private NotificationContact toDomain(NotificationContactDocument document) {
        return NotificationContact.builder()
                .userId(UUID.fromString(document.getUserId()))
                .email(document.getEmail())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
