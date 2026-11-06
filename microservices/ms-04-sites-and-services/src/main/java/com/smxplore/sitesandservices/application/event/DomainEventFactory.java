package com.smxplore.sitesandservices.application.event;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.event.DomainEvent;
import com.smxplore.sitesandservices.domain.model.event.EventTypes;
import com.smxplore.sitesandservices.domain.model.event.EventVersions;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class DomainEventFactory {

    private final String producer;
    private final Clock clock;

    public DomainEvent categoryChanged(Category category, String correlationId) {
        return event(
                EventTypes.CATALOG_CATEGORY_CHANGED,
                category.categoryId(),
                correlationId,
                categoryPayload(category)
        );
    }

    public Map<String, Object> categoryPayload(Category category) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("categoryId", category.categoryId().toString());
        payload.put("name", category.name());
        payload.put("scope", category.scope().name());
        payload.put("parentId", category.parentId() == null ? null : category.parentId().toString());
        payload.put("updatedAt", category.updatedAt().toString());
        return payload;
    }

    /**
     * Zona creada o modificada. ms-05 la consume para construir el nodo {@code :Zona} del grafo y
     * enlazar los sitios que pertenecen a ella.
     */
    public DomainEvent zoneChanged(Zone zone, String correlationId) {
        return event(
                EventTypes.CATALOG_ZONE_CHANGED,
                zone.zoneId(),
                correlationId,
                zonePayload(zone)
        );
    }

    public Map<String, Object> zonePayload(Zone zone) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zoneId", zone.zoneId().toString());
        payload.put("managerId", zone.managerId().toString());
        payload.put("name", zone.name());
        // type y description son opcionales en el contrato: si no vienen, el campo no se publica.
        if (zone.type() != null) {
            payload.put("type", zone.type());
        }
        if (zone.description() != null) {
            payload.put("description", zone.description());
        }
        payload.put("status", zone.status().name());
        payload.put("updatedAt", zone.updatedAt().toString());
        return payload;
    }

    /**
     * v2: adds {@code managerId}. ms-03 needs it to know which manager owns the site and refresh
     * the snapshot it keeps on its events, so the field is required from this version on.
     */
    public DomainEvent siteChanged(Site site, String correlationId) {
        return event(
                EventTypes.CATALOG_SITE_CHANGED,
                EventVersions.CATALOG_SITE_CHANGED,
                site.siteId(),
                correlationId,
                sitePayload(site)
        );
    }

    public Map<String, Object> sitePayload(Site site) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("siteId", site.siteId().toString());
        payload.put("name", site.name());
        payload.put("categoryId", site.categoryId().toString());
        payload.put("managerId", site.managerId() == null ? null : site.managerId().toString());
        // zoneId es obligatorio en el dominio; el contrato v2 lo declara opcional para no romper
        // consumidores que todavia no lo leen.
        payload.put("zoneId", site.zoneId() == null ? null : site.zoneId().toString());
        payload.put("latitude", site.latitude());
        payload.put("longitude", site.longitude());
        payload.put("status", site.status().name());
        payload.put("updatedAt", site.updatedAt().toString());
        return payload;
    }

    public DomainEvent serviceChanged(Service service, String correlationId) {
        return event(
                EventTypes.CATALOG_SERVICE_CHANGED,
                service.serviceId(),
                correlationId,
                servicePayload(service)
        );
    }

    public Map<String, Object> servicePayload(Service service) {
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
        return payload;
    }

    public DomainEvent reservationChanged(Reservation reservation, String correlationId) {
        List<Map<String, Object>> items = reservation.items().stream()
                .map(item -> {
                    Map<String, Object> payloadItem = new LinkedHashMap<>();
                    payloadItem.put("itemType", "SERVICE");
                    payloadItem.put("itemId", item.serviceId().toString());
                    payloadItem.put("providerId", item.providerId().toString());
                    payloadItem.put("serviceName", item.serviceName());
                    payloadItem.put("people", item.people());
                    payloadItem.put("total", item.total());
                    return payloadItem;
                })
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", reservation.reservationId().toString());
        payload.put("touristId", reservation.touristId().toString());
        payload.put("bookedFor", reservation.bookedFor().toString());
        payload.put("status", reservation.status().name());
        payload.put("items", items);
        payload.put("updatedAt", reservation.updatedAt().toString());
        return event(
                EventTypes.BOOKINGS_RESERVATION_CHANGED,
                reservation.reservationId(),
                correlationId,
                payload
        );
    }

    public DomainEvent reviewChanged(Review review, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewId", review.reviewId().toString());
        payload.put("touristId", review.touristId().toString());
        payload.put("itemType", review.itemType().name());
        payload.put("itemId", review.itemId().toString());
        payload.put("rating", review.rating());
        payload.put("status", review.status().name());
        if (review.response() != null && !review.response().isBlank() && review.respondedAt() != null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("text", review.response());
            response.put("respondedAt", review.respondedAt().toString());
            payload.put("response", response);
        }
        payload.put("updatedAt", review.updatedAt().toString());
        return event(EventTypes.REVIEWS_REVIEW_CHANGED, review.reviewId(), correlationId, payload);
    }

    private DomainEvent event(
            String eventType,
            UUID aggregateId,
            String correlationId,
            Map<String, Object> payload
    ) {
        // La version sale siempre de EventVersions: un productor no puede declarar una y emitir otra.
        return event(eventType, EventVersions.of(eventType), aggregateId, correlationId, payload);
    }

    private DomainEvent event(
            String eventType,
            int eventVersion,
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
                eventVersion,
                now,
                aggregateId,
                effectiveCorrelationId,
                producer,
                payload
        );
    }
}
