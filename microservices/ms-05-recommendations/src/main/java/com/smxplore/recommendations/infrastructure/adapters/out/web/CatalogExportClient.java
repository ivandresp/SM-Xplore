package com.smxplore.recommendations.infrastructure.adapters.out.web;

import com.smxplore.recommendations.domain.exception.DomainStateException;
import com.smxplore.recommendations.domain.exception.ForbiddenOperationException;
import com.smxplore.recommendations.domain.exception.InvalidReindexException;
import com.smxplore.recommendations.domain.model.catalog.CatalogEntity;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads ms-04's catalog export and turns every item into a {@link ProjectedEvent}. ms-04 publishes
 * the same payload as the Kafka events, so the export item is wrapped in the event envelope and
 * decoded by the regular decoder: the projection validates both paths identically.
 */
@RequiredArgsConstructor
public class CatalogExportClient implements CatalogExportPort {

    static final String CORRELATION_ID = "ms-05-reindex";
    static final String PRODUCER = "ms-04";
    /** Version assumed when ms-04 does not send {@code eventVersion} (older builds). */
    static final int LEGACY_EVENT_VERSION = 1;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ProjectionEventDecoder decoder;
    private final ReindexProperties properties;

    private WebClient client;

    @PostConstruct
    public void init() {
        Duration timeout = properties.getTimeout();
        HttpClient httpClient = HttpClient.create().responseTimeout(timeout);
        this.client = webClientBuilder
                .baseUrl(properties.getCatalogBaseUrl())
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
        return client.get()
                .uri(builder -> {
                    builder.path("/api/v1/admin/catalog/export")
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
                                .flatMap(body -> Mono.error(statusError(status, body)));
                    }
                    return response.bodyToMono(String.class)
                            .map(body -> decodePage(entity, body));
                });
    }

    private RuntimeException statusError(HttpStatusCode status, String body) {
        String detail = body == null ? "" : body.strip();
        if (detail.length() > 200) {
            detail = detail.substring(0, 200);
        }
        if (status.value() == 401 || status.value() == 403) {
            return new ForbiddenOperationException(
                    "ms-04 rejected the catalog export request with " + status.value()
                            + "; an administrator token is required");
        }
        return new DomainStateException("ms-04 catalog export returned " + status.value()
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

        int eventVersion = eventVersion(item, entity, payload);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", reindexEventId(eventType, aggregateId, updatedAt).toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", eventVersion);
        envelope.put("occurredAt", updatedAt.toString());
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("correlationId", CORRELATION_ID);
        envelope.put("producer", PRODUCER);
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
     * <p>ms-04 sends {@code eventVersion} in every item; when an older build omits it the version is
     * inferred from the payload instead of assuming v1: a site payload that carries
     * {@code managerId} is v2, so the projection applies the v2 validation (and does not accept a
     * v2 payload while believing it is v1).
     */
    private int eventVersion(JsonNode item, CatalogEntity entity, JsonNode payload) {
        JsonNode version = item.get("eventVersion");
        if (version != null && version.isIntegralNumber() && version.canConvertToInt()) {
            return version.asInt();
        }
        JsonNode unsupported = item.get("eventVersion");
        if (unsupported != null && !unsupported.isNull()) {
            throw new InvalidReindexException("catalog export eventVersion must be an integer");
        }
        // ms-04 publica catalog.site.changed v2 desde antes de exponer la version, asi que su feed
        // siempre es v2; inferir v2 solo cuando hay managerId validaria como v1 un sitio sin gestora.
        if (entity == CatalogEntity.SITES) {
            return 2;
        }
        return LEGACY_EVENT_VERSION;
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
            case CATEGORIES -> "catalog.category.changed";
            case SITES -> "catalog.site.changed";
            case SERVICES -> "catalog.service.changed";
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
