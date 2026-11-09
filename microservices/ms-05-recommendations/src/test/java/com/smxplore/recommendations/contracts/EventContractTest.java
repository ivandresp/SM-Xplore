package com.smxplore.recommendations.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.model.event.ActivityChangedEvent;
import com.smxplore.recommendations.domain.model.event.CategoryChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventItemChangedEvent;
import com.smxplore.recommendations.domain.model.event.EventTopics;
import com.smxplore.recommendations.domain.model.event.ReviewChangedEvent;
import com.smxplore.recommendations.domain.model.event.ServiceChangedEvent;
import com.smxplore.recommendations.domain.model.event.SiteChangedEvent;
import com.smxplore.recommendations.domain.model.event.ZoneChangedEvent;
import com.smxplore.recommendations.infrastructure.adapters.in.kafka.EventConsumerProperties;
import com.smxplore.recommendations.infrastructure.adapters.in.kafka.ProjectionEventDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventContractTest {

    private static final String BASE_IRI = "https://smxplore.local/contracts/events/";

    private final ObjectMapper schemaMapper = new ObjectMapper();
    private final tools.jackson.databind.ObjectMapper eventMapper =
            new tools.jackson.databind.ObjectMapper();
    private final ProjectionEventDecoder decoder = new ProjectionEventDecoder(
            eventMapper,
            new EventConsumerProperties(),
            Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC));
    private final Path contractsDirectory =
            Path.of(System.getProperty("contracts.dir", "../../contracts/events"));

    @Test
    void everySchemaHasAValidExample() throws Exception {
        Map<String, String> schemaTexts = schemaTexts();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaLoaders(loaders -> loaders.schemas(schemaTexts)));
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();

        List<Path> schemas;
        try (Stream<Path> paths = Files.list(contractsDirectory)) {
            schemas = paths
                    .filter(path -> path.getFileName().toString().endsWith(".v1.json")
                            || path.getFileName().toString().endsWith(".v2.json"))
                    .filter(path -> !path.getFileName().toString().startsWith("envelope."))
                    .sorted()
                    .toList();
        }

        assertThat(schemas).isNotEmpty();
        for (Path schemaPath : schemas) {
            String fileName = schemaPath.getFileName().toString();
            Path examplePath = contractsDirectory.resolve("examples").resolve(
                    fileName.substring(0, fileName.length() - ".json".length()) + ".example.json");
            assertThat(examplePath)
                    .as("Missing example for %s", fileName)
                    .exists();

            JsonNode schemaNode = schemaMapper.readTree(Files.readString(schemaPath));
            JsonSchema schema = factory.getSchema(
                    SchemaLocation.of(BASE_IRI + fileName), schemaNode, config);
            Set<ValidationMessage> errors = schema.validate(
                    schemaMapper.readTree(Files.readString(examplePath)));

            assertThat(errors)
                    .as("Contract violations in %s", examplePath.getFileName())
                    .isEmpty();
        }
    }

    @Test
    void consumedTopicsMatchTheContractCatalog() throws Exception {
        Set<String> schemaEventTypes;
        try (Stream<Path> paths = Files.list(contractsDirectory)) {
            schemaEventTypes = paths
                    .filter(path -> path.getFileName().toString().endsWith(".v1.json"))
                    .filter(path -> !path.getFileName().toString().equals("envelope.v1.json"))
                    .map(path -> path.getFileName().toString()
                            .substring(0, path.getFileName().toString().length() - ".v1.json".length()))
                    .collect(Collectors.toSet());
        }

        assertThat(schemaEventTypes).containsAll(EventTopics.CONSUMED);
        assertThat(schemaEventTypes).contains(EventTopics.BOOKINGS_RESERVATION_CHANGED);
        assertThat(EventTopics.CONSUMED).doesNotContain(EventTopics.BOOKINGS_RESERVATION_CHANGED);
    }

    @Test
    void decoderAcceptsEveryConsumedExample() throws Exception {
        for (String topic : EventTopics.CONSUMED) {
            for (int version : versionsFor(topic)) {
                Path examplePath = examplePath(topic, version);
                String json = Files.readString(examplePath);
                String key = eventMapper.readTree(json).get("aggregateId").asString();

                var event = decoder.decode(topic, key, json);

                assertThat(event.eventType()).isEqualTo(topic);
                assertThat(event.metadata().eventVersion()).isEqualTo(version);
                assertThat(event)
                        .as("Example of %s v%d must decode to %s", topic, version, expectedEventType(topic))
                        .isInstanceOf(expectedEventType(topic));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("consumedTopics")
    void everyConsumedTopicRejectsAnEventWithAMissingRequiredField(String topic) throws Exception {
        String json = Files.readString(examplePath(topic));
        String key = eventMapper.readTree(json).get("aggregateId").asString();
        var invalid = eventMapper.readTree(json);
        ((tools.jackson.databind.node.ObjectNode) invalid.get("payload"))
                .remove(requiredPayloadField(topic));

        assertThatThrownBy(() -> decoder.decode(topic, key, eventMapper.writeValueAsString(invalid)))
                .as("Removing %s from the %s example must be rejected", requiredPayloadField(topic), topic)
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void decoderRejectsReservationEvents() throws Exception {
        String json = Files.readString(examplePath(EventTopics.BOOKINGS_RESERVATION_CHANGED));
        String key = eventMapper.readTree(json).get("aggregateId").asString();

        assertThatThrownBy(() -> decoder.decode(
                EventTopics.BOOKINGS_RESERVATION_CHANGED, key, json))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("not consumed");
    }

    @Test
    void schemaRejectsAnEventWithAMissingRequiredField() throws Exception {
        Map<String, String> schemaTexts = schemaTexts();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaLoaders(loaders -> loaders.schemas(schemaTexts)));
        String fileName = EventTopics.CATALOG_SITE_CHANGED + ".v1.json";
        JsonSchema schema = factory.getSchema(
                SchemaLocation.of(BASE_IRI + fileName),
                schemaMapper.readTree(Files.readString(contractsDirectory.resolve(fileName))),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());

        ObjectNode invalid = (ObjectNode) schemaMapper.readTree(
                Files.readString(examplePath(EventTopics.CATALOG_SITE_CHANGED)));
        ((ObjectNode) invalid.get("payload")).remove("name");

        assertThat(schema.validate(invalid)).isNotEmpty();
    }

    private Map<String, String> schemaTexts() throws Exception {
        Map<String, String> schemas = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(contractsDirectory)) {
            List<Path> files = paths
                    .filter(path -> path.getFileName().toString().endsWith(".v1.json")
                            || path.getFileName().toString().endsWith(".v2.json"))
                    .sorted()
                    .toList();
            for (Path path : files) {
                schemas.put(BASE_IRI + path.getFileName(), Files.readString(path));
            }
        }
        return schemas;
    }

    private Path examplePath(String topic) {
        return examplePath(topic, 1);
    }

    private Path examplePath(String topic, int version) {
        return contractsDirectory.resolve("examples").resolve(topic + ".v" + version + ".example.json");
    }

    private static Stream<String> consumedTopics() {
        return EventTopics.CONSUMED.stream();
    }

    /**
     * Campo obligatorio del payload de cada topico consumido. Se elimina del ejemplo real del
     * contrato para verificar que el decoder lanza {@link InvalidEventException} y no acepta a
     * medias un evento incompleto. No inventa payloads: muta el fixture versionado.
     */
    private static String requiredPayloadField(String topic) {
        return switch (topic) {
            case EventTopics.REVIEWS_REVIEW_CHANGED -> "rating";
            default -> "name";
        };
    }

    private static Class<?> expectedEventType(String topic) {
        return switch (topic) {
            case EventTopics.CATALOG_SITE_CHANGED -> SiteChangedEvent.class;
            case EventTopics.CATALOG_SERVICE_CHANGED -> ServiceChangedEvent.class;
            case EventTopics.CATALOG_CATEGORY_CHANGED -> CategoryChangedEvent.class;
            case EventTopics.CATALOG_ZONE_CHANGED -> ZoneChangedEvent.class;
            case EventTopics.ACTIVITIES_EVENT_CHANGED -> EventItemChangedEvent.class;
            case EventTopics.ACTIVITIES_ACTIVITY_CHANGED -> ActivityChangedEvent.class;
            case EventTopics.REVIEWS_REVIEW_CHANGED -> ReviewChangedEvent.class;
            default -> throw new IllegalArgumentException("Unexpected consumed topic: " + topic);
        };
    }

    private static List<Integer> versionsFor(String topic) {
        return switch (topic) {
            case EventTopics.CATALOG_SITE_CHANGED,
                 EventTopics.ACTIVITIES_ACTIVITY_CHANGED,
                 EventTopics.ACTIVITIES_EVENT_CHANGED -> List.of(1, 2);
            default -> List.of(1);
        };
    }
}
