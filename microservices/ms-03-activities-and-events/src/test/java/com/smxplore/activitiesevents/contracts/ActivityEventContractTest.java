package com.smxplore.activitiesevents.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.smxplore.activitiesevents.application.outbox.ActivityChangedEventFactory;
import com.smxplore.activitiesevents.application.outbox.EventChangedEventFactory;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the generated activities.* v2 events against the versioned schemas. A v2 event is
 * validated against envelope.v2.json, because envelope.v1.json pins eventVersion to 1.
 */
class ActivityEventContractTest {

    private static final String BASE_IRI = "https://smxplore.local/contracts/events/";
    private static final Path CONTRACTS = Path.of(
            System.getProperty("contracts.dir", "../../contracts/events"));

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ActivityChangedEventFactory activityFactory = new ActivityChangedEventFactory(mapper);
    private final EventChangedEventFactory eventFactory = new EventChangedEventFactory(mapper);

    @Test
    void activityExampleMatchesTheContract() throws Exception {
        assertThat(validate("activities.activity.changed", "activities.activity.changed.v2.example.json"))
                .isEmpty();
    }

    @Test
    void eventExampleMatchesTheContract() throws Exception {
        assertThat(validate("activities.event.changed", "activities.event.changed.v2.example.json"))
                .isEmpty();
    }

    @Test
    void generatedActivityEventMatchesTheContract() throws Exception {
        var startsAt = Instant.parse("2026-12-01T15:00:00Z");
        var activity = Activity.builder()
                .siteId(UUID.randomUUID())
                .guideId(UUID.randomUUID())
                .name("Snorkel en Playa Rosa")
                .category("Deportes acuáticos")
                .status(ActivityStatus.PUBLISHED)
                .price(new BigDecimal("80000.00"))
                .maxCapacity(12)
                .durationMinutes(120)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(7_200))
                .build();

        var event = activityFactory.activityChanged(activity).block();
        assertThat(event).isNotNull();

        var envelope = mapper.readTree(event.payload());
        assertThat(validate("activities.activity.changed", null, envelope)).isEmpty();
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(2);
        assertThat(envelope.get("payload").get("category").asText()).isEqualTo("Deportes acuáticos");
        assertThat(envelope.get("payload").has("latitude")).isFalse();
    }

    @Test
    void generatedEventEventMatchesTheContract() throws Exception {
        var startsAt = Instant.parse("2026-12-01T15:00:00Z");
        var catalogEvent = Event.builder()
                .siteId(UUID.randomUUID())
                .managerId(UUID.randomUUID())
                .siteName("Quinta de San Pedro")
                .name("Feria artesanal")
                .category("Cultura")
                .status(EventStatus.POSTPONED)
                .price(new BigDecimal("25000.00"))
                .maxCapacity(200)
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(21_600))
                .build();

        var event = eventFactory.eventChanged(catalogEvent).block();
        assertThat(event).isNotNull();

        var envelope = mapper.readTree(event.payload());
        assertThat(validate("activities.event.changed", null, envelope)).isEmpty();
        assertThat(envelope.get("payload").get("status").asText()).isEqualTo("POSTPONED");
    }

    private List<Error> validate(String eventType, String exampleFile) throws Exception {
        return validate(eventType, exampleFile, null);
    }

    private List<Error> validate(String eventType, String exampleFile, JsonNode payload)
            throws Exception {
        JsonNode node = payload != null
                ? payload
                : mapper.readTree(Files.readString(CONTRACTS.resolve("examples").resolve(exampleFile)));
        return schema(eventType).validate(node.toString(), InputFormat.JSON);
    }

    private Schema schema(String eventType) throws Exception {
        Map<String, String> schemas = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(CONTRACTS)) {
            List<Path> files = paths
                    .filter(path -> {
                        var name = path.getFileName().toString();
                        return name.endsWith(".v1.json") || name.endsWith(".v2.json");
                    })
                    .sorted()
                    .toList();
            for (Path path : files) {
                schemas.put(BASE_IRI + path.getFileName(), Files.readString(path));
            }
        }
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder
                        .schemas(schemas)
                        .schemaRegistryConfig(SchemaRegistryConfig.builder()
                                .formatAssertionsEnabled(true)
                                .build()));
        String fileName = eventType + ".v2.json";
        return registry.getSchema(SchemaLocation.of(BASE_IRI + fileName));
    }
}
