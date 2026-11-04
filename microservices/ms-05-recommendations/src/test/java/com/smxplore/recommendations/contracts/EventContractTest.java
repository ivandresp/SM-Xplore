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
import com.smxplore.recommendations.domain.model.event.EventTopics;
import com.smxplore.recommendations.infrastructure.adapters.in.kafka.ProjectionEventDecoder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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
    private final ProjectionEventDecoder decoder = new ProjectionEventDecoder(eventMapper);
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
                    .filter(path -> path.getFileName().toString().endsWith(".v1.json"))
                    .filter(path -> !path.getFileName().toString().equals("envelope.v1.json"))
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
            Path examplePath = examplePath(topic);
            String json = Files.readString(examplePath);
            String key = eventMapper.readTree(json).get("aggregateId").asString();

            var event = decoder.decode(topic, key, json);

            assertThat(event.eventType()).isEqualTo(topic);
            assertThat(event.metadata().eventVersion()).isEqualTo(1);
        }
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
                    .filter(path -> path.getFileName().toString().endsWith(".v1.json"))
                    .sorted()
                    .toList();
            for (Path path : files) {
                schemas.put(BASE_IRI + path.getFileName(), Files.readString(path));
            }
        }
        return schemas;
    }

    private Path examplePath(String topic) {
        return contractsDirectory.resolve("examples").resolve(topic + ".v1.example.json");
    }
}
