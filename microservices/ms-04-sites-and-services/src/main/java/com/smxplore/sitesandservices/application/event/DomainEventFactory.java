package com.smxplore.sitesandservices.application.event;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.event.DomainEvent;
import com.smxplore.sitesandservices.domain.model.event.EventTypes;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class DomainEventFactory {

    private final String producer;
    private final Clock clock;

    public DomainEvent categoryChanged(Category category, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("categoryId", category.categoryId().toString());
        payload.put("name", category.name());
        payload.put("scope", category.scope().name());
        payload.put("parentId", category.parentId() == null ? null : category.parentId().toString());
        payload.put("updatedAt", category.updatedAt().toString());
        return event(EventTypes.CATALOG_CATEGORY_CHANGED, category.categoryId(), correlationId, payload);
    }

    public DomainEvent siteChanged(Site site, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("siteId", site.siteId().toString());
        payload.put("name", site.name());
        payload.put("categoryId", site.categoryId().toString());
        payload.put("latitude", site.latitude());
        payload.put("longitude", site.longitude());
        payload.put("status", site.status().name());
        payload.put("updatedAt", site.updatedAt().toString());
        return event(EventTypes.CATALOG_SITE_CHANGED, site.siteId(), correlationId, payload);
    }

    public DomainEvent serviceChanged(Service service, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serviceId", service.serviceId().toString());
        payload.put("siteId", service.siteId().toString());
        payload.put("name", service.name());
        payload.put("type", service.type());
        payload.put("categoryId", service.categoryId().toString());
        payload.put("latitude", service.latitude());
        payload.put("longitude", service.longitude());
        payload.put("status", service.status().name());
        payload.put("updatedAt", service.updatedAt().toString());
        return event(EventTypes.CATALOG_SERVICE_CHANGED, service.serviceId(), correlationId, payload);
    }

    public DomainEvent reviewChanged(Review review, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewId", review.reviewId().toString());
        payload.put("touristId", review.touristId().toString());
        payload.put("itemType", review.itemType().name());
        payload.put("itemId", review.itemId().toString());
        payload.put("rating", review.rating());
        payload.put("status", review.status().name());
        payload.put("updatedAt", review.updatedAt().toString());
        return event(EventTypes.REVIEWS_REVIEW_CHANGED, review.reviewId(), correlationId, payload);
    }

    private DomainEvent event(
            String eventType,
            UUID aggregateId,
            String correlationId,
            Map<String, Object> payload
    ) {
        Instant now = Instant.now(clock);
        String effectiveCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId;
        return new DomainEvent(
                UUID.randomUUID(),
                eventType,
                1,
                now,
                aggregateId,
                effectiveCorrelationId,
                producer,
                payload
        );
    }
}
