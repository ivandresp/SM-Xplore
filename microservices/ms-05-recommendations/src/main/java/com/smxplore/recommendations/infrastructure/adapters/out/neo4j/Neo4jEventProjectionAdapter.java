package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

import com.smxplore.recommendations.domain.model.event.ActivityChangedEvent;
import com.smxplore.recommendations.domain.model.event.CategoryChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventItemChangedEvent;
import com.smxplore.recommendations.domain.model.event.ProjectedEvent;
import com.smxplore.recommendations.domain.model.event.ProjectionResult;
import com.smxplore.recommendations.domain.model.event.ReviewChangedEvent;
import com.smxplore.recommendations.domain.model.event.ServiceChangedEvent;
import com.smxplore.recommendations.domain.model.event.SiteChangedEvent;
import com.smxplore.recommendations.domain.ports.out.EventProjectionPort;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.neo4j.driver.reactivestreams.ReactiveTransactionContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class Neo4jEventProjectionAdapter implements EventProjectionPort {

    private final Driver driver;

    @Override
    public Mono<ProjectionResult> apply(ProjectedEvent event) {
        return Mono.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class)),
                session -> Flux.from(session.executeWrite(
                                transaction -> applyInTransaction(transaction, event)))
                        .singleOrEmpty(),
                session -> Flux.from(session.close()).then()
        );
    }

    private Mono<ProjectionResult> applyInTransaction(ReactiveTransactionContext transaction, ProjectedEvent event) {
        Map<String, Object> eventParams = Map.of("eventId", event.metadata().eventId().toString());

        return exists(transaction, Neo4jProjectionQueries.PROCESSED_EXISTS, eventParams)
                .filter(Boolean::booleanValue)
                .map(ignored -> ProjectionResult.DUPLICATE)
                .switchIfEmpty(Mono.defer(() -> applyIfNotStale(transaction, event)));
    }

    private Mono<ProjectionResult> applyIfNotStale(
            ReactiveTransactionContext transaction,
            ProjectedEvent event
    ) {
        return isStale(transaction, event)
                .filter(Boolean::booleanValue)
                .flatMap(ignored -> markProcessed(transaction, event, ProjectionResult.STALE)
                        .thenReturn(ProjectionResult.STALE))
                .switchIfEmpty(Mono.defer(() -> applyNewEvent(transaction, event)));
    }

    private Mono<ProjectionResult> applyNewEvent(
            ReactiveTransactionContext transaction,
            ProjectedEvent event
    ) {
        return applyEvent(transaction, event)
                .then(markProcessed(transaction, event, ProjectionResult.APPLIED))
                .thenReturn(ProjectionResult.APPLIED);
    }

    private Mono<Boolean> exists(
            ReactiveTransactionContext transaction,
            String query,
            Map<String, Object> params
    ) {
        return Mono.from(transaction.run(query, params))
                .flatMap(result -> Flux.from(result.records()).collectList())
                .map(records -> !records.isEmpty());
    }

    private Mono<Boolean> isStale(ReactiveTransactionContext transaction, ProjectedEvent event) {
        String query;
        Map<String, Object> params = new HashMap<>();
        params.put("updatedAt", event.updatedAt().toString());

        if (event instanceof CategoryChangedEvent category) {
            query = Neo4jProjectionQueries.STALE_CATEGORY;
            params.put("id", category.categoryId().toString());
        } else if (event instanceof ReviewChangedEvent review) {
            query = Neo4jProjectionQueries.STALE_REVIEW;
            params.put("touristId", review.touristId().toString());
            params.put("reviewId", review.reviewId().toString());
            params.put("itemId", review.itemId().toString());
        } else {
            query = Neo4jProjectionQueries.STALE_ITEM;
            params.put("id", event.metadata().aggregateId().toString());
        }

        return Mono.from(transaction.run(query, params))
                .flatMap(result -> Flux.from(result.records()).collectList())
                .map(records -> !records.isEmpty()
                        && records.getFirst().get("stale").asBoolean(false));
    }

    private Mono<Void> applyEvent(ReactiveTransactionContext transaction, ProjectedEvent event) {
        return switch (event) {
            case CategoryChangedEvent category ->
                    run(transaction, Neo4jProjectionQueries.UPSERT_CATEGORY, categoryParams(category));
            case SiteChangedEvent site ->
                    run(transaction, Neo4jProjectionQueries.UPSERT_SITE, siteParams(site));
            case ServiceChangedEvent service ->
                    run(transaction, Neo4jProjectionQueries.UPSERT_SERVICE, serviceParams(service));
            case ActivityChangedEvent activity ->
                    run(transaction, Neo4jProjectionQueries.UPSERT_ACTIVITY, activityParams(activity));
            case EventItemChangedEvent eventItem ->
                    run(transaction, Neo4jProjectionQueries.UPSERT_EVENT, eventItemParams(eventItem));
            case ReviewChangedEvent review ->
                    run(transaction, Neo4jProjectionQueries.UPSERT_REVIEW, reviewParams(review));
        };
    }

    private Mono<Void> markProcessed(
            ReactiveTransactionContext transaction,
            ProjectedEvent event,
            ProjectionResult result
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("eventId", event.metadata().eventId().toString());
        params.put("eventType", event.metadata().eventType());
        params.put("aggregateId", event.metadata().aggregateId().toString());
        params.put("occurredAt", event.metadata().occurredAt().toString());
        params.put("outcome", result.name());
        return run(transaction, Neo4jProjectionQueries.MARK_PROCESSED, params);
    }

    private Mono<Void> run(ReactiveTransactionContext transaction, String query, Map<String, Object> params) {
        return Mono.from(transaction.run(query, params))
                .flatMap(result -> Flux.from(result.consume()).then())
                .then();
    }

    private Map<String, Object> categoryParams(CategoryChangedEvent event) {
        Map<String, Object> params = baseParams(
                "categoryId", event.categoryId(),
                "updatedAt", event.updatedAt()
        );
        params.put("name", event.name());
        params.put("scope", event.scope().name());
        params.put("parentId", event.parentId() == null ? null : event.parentId().toString());
        return params;
    }

    private Map<String, Object> siteParams(SiteChangedEvent event) {
        Map<String, Object> params = baseParams(
                "siteId", event.siteId(),
                "updatedAt", event.updatedAt()
        );
        params.put("name", event.name());
        params.put("categoryId", event.categoryId().toString());
        params.put("latitude", event.latitude());
        params.put("longitude", event.longitude());
        params.put("status", event.status().name());
        return params;
    }

    private Map<String, Object> serviceParams(ServiceChangedEvent event) {
        Map<String, Object> params = baseParams(
                "serviceId", event.serviceId(),
                "updatedAt", event.updatedAt()
        );
        params.put("siteId", event.siteId().toString());
        params.put("name", event.name());
        params.put("type", event.type());
        params.put("categoryId", event.categoryId().toString());
        params.put("latitude", event.latitude());
        params.put("longitude", event.longitude());
        params.put("status", event.status().name());
        return params;
    }

    private Map<String, Object> activityParams(ActivityChangedEvent event) {
        Map<String, Object> params = baseParams(
                "activityId", event.activityId(),
                "updatedAt", event.updatedAt()
        );
        params.put("name", event.name());
        params.put("categoryId", event.categoryId().toString());
        params.put("siteId", event.siteId().toString());
        params.put("latitude", event.latitude());
        params.put("longitude", event.longitude());
        params.put("price", event.price());
        params.put("status", event.status().name());
        return params;
    }

    private Map<String, Object> eventItemParams(EventItemChangedEvent event) {
        Map<String, Object> params = baseParams(
                "eventId", event.itemId(),
                "updatedAt", event.updatedAt()
        );
        params.put("name", event.name());
        params.put("categoryId", event.categoryId().toString());
        params.put("siteId", event.siteId().toString());
        params.put("latitude", event.latitude());
        params.put("longitude", event.longitude());
        params.put("price", event.price());
        params.put("startsAt", event.startsAt().toString());
        params.put("endsAt", event.endsAt() == null ? null : event.endsAt().toString());
        params.put("status", event.status().name());
        return params;
    }

    private Map<String, Object> reviewParams(ReviewChangedEvent event) {
        Map<String, Object> params = baseParams(
                "reviewId", event.reviewId(),
                "updatedAt", event.updatedAt()
        );
        params.put("touristId", event.touristId().toString());
        params.put("itemType", event.itemType().name());
        params.put("itemId", event.itemId().toString());
        params.put("rating", event.rating());
        params.put("status", event.status().name());
        return params;
    }

    private Map<String, Object> baseParams(String idName, Object id, String timeName, Object time) {
        Map<String, Object> params = new HashMap<>();
        params.put(idName, id.toString());
        params.put(timeName, time.toString());
        return params;
    }
}
