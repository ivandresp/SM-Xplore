package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationFilter;
import com.smxplore.sitesandservices.domain.ports.out.NotificationRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.NotificationDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MongoNotificationRepositoryAdapter implements NotificationRepositoryPort {

    private final ReactiveMongoTemplate mongoTemplate;
    private final MongoNotificationMapper mapper;

    @Override
    public Mono<Void> saveIfAbsent(Notification notification) {
        Query query = Query.query(Criteria.where("_id")
                .is(notification.notificationId().toString()));
        Update update = new Update()
                .setOnInsert("recipientId", notification.recipientId().toString())
                .setOnInsert("recipientRole", notification.recipientRole().name())
                .setOnInsert("reservationId", notification.reservationId().toString())
                .setOnInsert("eventId", notification.eventId().toString())
                .setOnInsert("type", notification.type().name())
                .setOnInsert("title", notification.title())
                .setOnInsert("message", notification.message())
                .setOnInsert("createdAt", notification.createdAt());
        return mongoTemplate.upsert(query, update, NotificationDocument.class).then();
    }

    @Override
    public Mono<Notification> findById(UUID notificationId) {
        return mongoTemplate.findById(notificationId.toString(), NotificationDocument.class)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<PageResult<Notification>> findAll(NotificationFilter filter) {
        Criteria criteria = Criteria.where("recipientId").is(filter.recipientId().toString());
        if (filter.unreadOnly()) {
            criteria.and("readAt").is(null);
        }
        Query query = Query.query(criteria);
        Query pageQuery = Query.of(query).with(PageRequest.of(
                filter.page(),
                filter.size(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        ));

        Mono<Long> total = mongoTemplate.count(query, NotificationDocument.class);
        Mono<List<Notification>> content = mongoTemplate.find(
                        pageQuery, NotificationDocument.class)
                .map(mapper::toDomain)
                .collectList();

        return Mono.zip(total, content)
                .map(tuple -> new PageResult<>(
                        tuple.getT2(), filter.page(), filter.size(), tuple.getT1()));
    }

    @Override
    public Mono<Void> markRead(UUID notificationId, Instant readAt) {
        Query query = Query.query(Criteria.where("_id").is(notificationId.toString()));
        Update update = new Update().set("readAt", readAt);
        return mongoTemplate.updateFirst(query, update, NotificationDocument.class).then();
    }
}
