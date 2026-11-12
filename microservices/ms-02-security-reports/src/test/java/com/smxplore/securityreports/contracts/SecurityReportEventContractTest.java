package com.smxplore.securityreports.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.smxplore.securityreports.application.event.SecurityReportEventFactory;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.Location;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.infrastructure.adapters.out.event.DomainEventJsonSerializer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityReportEventContractTest {

    private static final String BASE_IRI = "https://smxplore.local/contracts/events/";
    private static final String EVENT_TYPE = "security.report.changed";
    private static final Path CONTRACTS = Path.of(
            System.getProperty("contracts.dir", "../../contracts/events"));

    private final ObjectMapper schemaMapper = new ObjectMapper();
    private final tools.jackson.databind.ObjectMapper eventMapper =
            new tools.jackson.databind.ObjectMapper();

    @Test
    void exampleMatchesTheContract() throws Exception {
        Schema schema = schema(EVENT_TYPE);
        JsonNode example = schemaMapper.readTree(Files.readString(
                CONTRACTS.resolve("examples").resolve(EVENT_TYPE + ".v1.example.json")));

        List<Error> errors = schema.validate(example.toString(), InputFormat.JSON);

        assertThat(errors).isEmpty();
    }

    @Test
    void generatedEventMatchesTheContract() throws Exception {
        Instant now = Instant.parse("2026-11-04T17:00:00Z");
        SecurityReport report = SecurityReport.builder()
                .id(UUID.fromString("88888888-8888-8888-8888-888888888801"))
                .user(new UserRef(
                        UUID.fromString("66666666-6666-6666-6666-666666666601"),
                        "Turista"))
                .title("Robo en la playa")
                .location(new Location(10.391, -75.479))
                .createdAt(now.minusSeconds(600))
                .updatedAt(now)
                .build();

        var factory = new SecurityReportEventFactory("ms-02", Clock.fixed(now, ZoneOffset.UTC));
        var serializer = new DomainEventJsonSerializer(eventMapper);
        String json = serializer.serialize(factory.reportChanged(report, "correlation-1"));

        List<Error> errors = schema(EVENT_TYPE).validate(json, InputFormat.JSON);

        assertThat(errors).isEmpty();
        assertThat(eventMapper.readTree(json).get("eventType").asString()).isEqualTo(EVENT_TYPE);
        assertThat(eventMapper.readTree(json).get("producer").asString()).isEqualTo("ms-02");
        assertThat(eventMapper.readTree(json).get("aggregateId").asString())
                .isEqualTo(report.getId().toString());
    }

    private Schema schema(String eventType) throws Exception {
        Map<String, String> schemas = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(CONTRACTS)) {
            List<Path> files = paths
                    .filter(path -> path.getFileName().toString().endsWith(".v1.json"))
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
        String fileName = eventType + ".v1.json";
        return registry.getSchema(SchemaLocation.of(BASE_IRI + fileName));
    }
}
