package com.smxplore.recommendations.infrastructure.adapters.out.web;

import com.smxplore.recommendations.domain.exception.DomainStateException;
import com.smxplore.recommendations.domain.exception.ForbiddenOperationException;
import com.smxplore.recommendations.domain.exception.InvalidReindexException;
import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;
import com.smxplore.recommendations.domain.model.event.EventTopics;
import com.smxplore.recommendations.domain.model.event.ProjectedEvent;
import com.smxplore.recommendations.domain.model.reindex.CatalogExportPage;
import com.smxplore.recommendations.domain.ports.out.CatalogExportPort;
import com.smxplore.recommendations.infrastructure.adapters.in.kafka.ProjectionEventDecoder;
import com.smxplore.recommendations.infrastructure.config.ReindexProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the administrative catalog export and turns every item into a {@link ProjectedEvent}.
 *
 * <p>Two producers expose the same export contract: ms-04 owns categories, sites and services,
 * ms-03 owns activities and events. The client resolves the base URL per {@link CatalogEntity},
 * but the wire format is identical, so both paths are decoded by the regular decoder and the
 * projection validates them exactly like a Kafka event.</p>
 */
@RequiredArgsConstructor
public class CatalogExportClient implements CatalogExportPort {

    static final String CORRELATION_ID = "ms-05-reindex";
    static final String PRODUCER_MS04 = "ms-04";
    static final String PRODUCER_MS03 = "ms-03-activities-and-events";
    /** Version assumed when ms-04 does not send {@code eventVersion} (older builds). */
    static final int LEGACY_EVENT_VERSION = 1;
    /** ms-03 only publishes v2 activities/events, so its export is always v2. */
    static final int MS03_EVENT_VERSION = 2;

    private static final String EXPORT_PATH = "/api/v1/admin/catalog/export";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ProjectionEventDecoder decoder;
    private final ReindexProperties properties;

    private Map<CatalogEntity, WebClient> clients;
    private Map<CatalogEntity, String> baseUrls;

    @PostConstruct
    public void init() {
        Duration timeout = properties.getTimeout();
        HttpClient httpClient = HttpClient.create().responseTimeout(timeout);
        Map<CatalogEntity, WebClient> byEntity = new EnumMap<>(CatalogEntity.class);
        Map<CatalogEntity, String> urls = new EnumMap<>(CatalogEntity.class);
        put(byEntity, urls, httpClient, CatalogEntity.CATEGORIES, properties.getCatalogBaseUrl());
        put(byEntity, urls, httpClient, CatalogEntity.SITES, properties.getCatalogBaseUrl());
        put(byEntity, urls, httpClient, CatalogEntity.SERVICES, properties.getCatalogBaseUrl());
        put(byEntity, urls, httpClient, CatalogEntity.ACTIVITIES, properties.getActivitiesBaseUrl());
        put(byEntity, urls, httpClient, CatalogEntity.EVENTS, properties.getEventsBaseUrl());
        this.clients = byEntity;
        this.baseUrls = urls;
    }

    private void put(Map<CatalogEntity, WebClient> clientsByEntity,
                     Map<CatalogEntity, String> urlsByEntity,
                     HttpClient httpClient,
                     CatalogEntity entity,
                     String baseUrl) {
        clientsByEntity.put(entity, buildClient(httpClient, baseUrl));
        urlsByEntity.put(entity, baseUrl);
    }

    private WebClient buildClient(HttpClient httpClient, String baseUrl) {
        return webClientBuilder
                .baseUrl(baseUrl)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public Mono<CatalogExportPage> fetch(String bearerToken, CatalogEntity entity, int page, Instant updatedSince) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Mono.error(new InvalidReindexException("authorization header is required"));
        }
        if (entity == null) {
            return Mono.error(new InvalidReindexException("entity is required"));
        }
        if (page < 0) {
            return Mono.error(new InvalidReindexException("page must be >= 0"));
        }
        WebClient client = clients.get(entity);
        if (client == null) {
            return Mono.error(new InvalidReindexException("entity is not supported: " + entity));
        }
        String baseUrl = baseUrls.get(entity);
        String host = hostOf(baseUrl);
        if (host == null || !properties.isAllowedHost(host)) {
            // MS05-03: never send the administrator bearer to a host outside the allowlist,
            // even if the environment changed after startup.
            return Mono.error(new InvalidReindexException(
                    "catalog export host is not allowed: " + host + " (entity " + entity + ")"));
        }
        return client.get()
                .uri(builder -> {
                    builder.path(EXPORT_PATH)
                            .queryParam("entity", entity.name())
                            .queryParam("page", page)
                            .queryParam("size", properties.getPageSize());
                    if (updatedSince != null) {
                        builder.queryParam("updatedSince", updatedSince.toString());
                    }
                    return builder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .header(HttpHeaders.ACCEPT, "application/json")
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (status.isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(statusError(entity, status, body)));
                    }
                    return response.bodyToMono(String.class)
                            .map(body -> decodePage(entity, body));
                });
    }

    /**
     * Host of a configured base URL, or {@code null} when it cannot be parsed. The allowlist
     * check runs on every request so a runtime change of the property cannot leak the token.
     */
    private static String hostOf(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            return URI.create(baseUrl).getHost();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private RuntimeException statusError(CatalogEntity entity, HttpStatusCode status, String body) {
        String detail = body == null ? "" : body.strip();
        if (detail.length() > 200) {
            detail = detail.substring(0, 200);
        }
        String producer = producer(entity);
        if (status.value() == 401 || status.value() == 403) {
            return new ForbiddenOperationException(
                    producer + " rejected the catalog export request with " + status.value()
                            + "; an administrator token is required");
        }
        return new DomainStateException(producer + " catalog export returned " + status.value()
                + (detail.isBlank() ? "" : ": " + detail));
    }

    private CatalogExportPage decodePage(CatalogEntity entity, String body) {
        JsonNode root = parse(body);
        JsonNode content = root.get("content");
        if (content == null || !content.isArray()) {
            throw new InvalidReindexException("catalog export content must be an array");
        }
        List<ProjectedEvent> events = new ArrayList<>();
        for (JsonNode item : content) {
            events.add(decodeItem(entity, item));
        }
        return new CatalogExportPage(
                events,
                requiredInt(root, "page"),
                requiredInt(root, "size"),
                requiredLong(root, "total"),
                requiredBoolean(root, "hasNext"));
    }

    private ProjectedEvent decodeItem(CatalogEntity entity, JsonNode item) {
        String eventType = requiredText(item, "eventType");
        UUID aggregateId = requiredUuid(item, "aggregateId");
        Instant updatedAt = requiredInstant(item, "updatedAt");
        JsonNode payload = item.get("payload");
        if (payload == null || !payload.isObject()) {
            throw new InvalidReindexException("catalog export payload must be an object");
        }
        if (!expectedEventType(entity).equals(eventType)) {
            throw new InvalidReindexException(
                    "catalog export for " + entity + " returned eventType " + eventType);
        }

        int eventVersion = eventVersion(item, entity);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", reindexEventId(eventType, aggregateId, updatedAt).toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", eventVersion);
        envelope.put("occurredAt", updatedAt.toString());
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("correlationId", CORRELATION_ID);
        envelope.put("producer", producer(entity));
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JacksonException exception) {
            throw new InvalidReindexException("catalog export item is not serializable", exception);
        }
        return decoder.decode(eventType, aggregateId.toString(), json);
    }

    /**
     * Version of the exported payload.
     *
     * <p>The exported {@code eventVersion} wins when it is present: the feed may publish a newer
     * version than ms-05 assumes, and the decoder is the component that validates it. When an
     * older build omits the field the version is inferred per producer: ms-04 has published
     * {@code catalog.site.changed} v2 since before exposing the version (so a manager-less site is
     * still v2), while ms-03 only publishes activities/events as v2 and its export mirrors that
     * payload.</p>
     */
    private int eventVersion(JsonNode item, CatalogEntity entity) {
        JsonNode version = item.get("eventVersion");
        if (version != null && version.isIntegralNumber() && version.canConvertToInt()) {
            return version.asInt();
        }
        if (version != null && !version.isNull()) {
            throw new InvalidReindexException("catalog export eventVersion must be an integer");
        }
        return switch (entity) {
            case SITES -> 2;
            case ACTIVITIES, EVENTS -> MS03_EVENT_VERSION;
            case CATEGORIES, SERVICES -> LEGACY_EVENT_VERSION;
        };
    }

    /**
     * Deterministic identifier: replaying the same page is a no-op thanks to the ProcessedEvent
     * marker, while an updated entity produces a new marker.
     */
    static UUID reindexEventId(String eventType, UUID aggregateId, Instant updatedAt) {
        String seed = eventType + ":" + aggregateId + ":" + updatedAt;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    static String expectedEventType(CatalogEntity entity) {
        return switch (entity) {
            case CATEGORIES -> EventTopics.CATALOG_CATEGORY_CHANGED;
            case SITES -> EventTopics.CATALOG_SITE_CHANGED;
            case SERVICES -> EventTopics.CATALOG_SERVICE_CHANGED;
            case ACTIVITIES -> EventTopics.ACTIVITIES_ACTIVITY_CHANGED;
            case EVENTS -> EventTopics.ACTIVITIES_EVENT_CHANGED;
        };
    }

    private static String producer(CatalogEntity entity) {
        return switch (entity) {
            case CATEGORIES, SITES, SERVICES -> PRODUCER_MS04;
            case ACTIVITIES, EVENTS -> PRODUCER_MS03;
        };
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            throw new InvalidReindexException("catalog export body is empty");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                throw new InvalidReindexException("catalog export body must be a JSON object");
            }
            return root;
        } catch (JacksonException exception) {
            throw new InvalidReindexException("catalog export body is not valid JSON", exception);
        }
    }

    private String requiredText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw new InvalidReindexException("catalog export " + field + " is required");
        }
        return node.asString();
    }

    private UUID requiredUuid(JsonNode parent, String field) {
        String value = requiredText(parent, field);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidReindexException("catalog export " + field + " must be a UUID", exception);
        }
    }

    private Instant requiredInstant(JsonNode parent, String field) {
        String value = requiredText(parent, field);
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new InvalidReindexException(
                    "catalog export " + field + " must be an ISO-8601 instant", exception);
        }
    }

    private int requiredInt(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new InvalidReindexException("catalog export " + field + " must be an integer");
        }
        return node.asInt();
    }

    private long requiredLong(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new InvalidReindexException("catalog export " + field + " must be an integer");
        }
        return node.asLong();
    }

    private boolean requiredBoolean(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isBoolean()) {
            throw new InvalidReindexException("catalog export " + field + " must be a boolean");
        }
        return node.asBoolean();
    }
}
