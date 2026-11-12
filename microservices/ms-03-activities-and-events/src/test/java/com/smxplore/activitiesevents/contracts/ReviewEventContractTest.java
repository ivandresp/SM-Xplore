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
import com.smxplore.activitiesevents.application.outbox.ReviewEventFactory;
import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the generated reviews.review.changed event against the versioned schema in
 * contracts/events. The contracts folder is resolved from the service folder, like ms-02 and ms-05.
 */
class ReviewEventContractTest {

    private static final String BASE_IRI = "https://smxplore.local/contracts/events/";
    private static final String EVENT_TYPE = "reviews.review.changed";
    private static final Path CONTRACTS = Path.of(
            System.getProperty("contracts.dir", "../../contracts/events"));

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ReviewEventFactory factory = new ReviewEventFactory(mapper);

    @Test
    void exampleMatchesTheContract() throws Exception {
        Schema schema = schema(EVENT_TYPE);
        JsonNode example = mapper.readTree(Files.readString(
                CONTRACTS.resolve("examples").resolve(EVENT_TYPE + ".v1.example.json")));

        List<Error> errors = schema.validate(example.toString(), InputFormat.JSON);

        assertThat(errors).isEmpty();
    }

    @Test
    void generatedEventMatchesTheContract() throws Exception {
        var review = Review.builder()
                .itemId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .itemType(ReviewItemType.ACTIVITY)
                .user(new ReviewUser(UUID.fromString("22222222-2222-2222-2222-222222222222"), "Ana"))
                .rating(5)
                .build();

        var event = factory.reviewChanged(review).block();

        assertThat(event).isNotNull();
        JsonNode envelope = mapper.readTree(event.payload());

        // The outbox stores the whole v1 envelope, so the contract validates the envelope too.
        List<Error> errors = schema(EVENT_TYPE).validate(envelope.toString(), InputFormat.JSON);

        assertThat(errors).isEmpty();
        assertThat(envelope.get("payload").get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(envelope.get("aggregateId").asText()).isEqualTo(review.getId().toString());
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
