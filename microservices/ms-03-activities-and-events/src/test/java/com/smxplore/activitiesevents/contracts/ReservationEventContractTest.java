package com.smxplore.activitiesevents.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.smxplore.activitiesevents.application.outbox.ReservationEventFactory;
import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
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

class ReservationEventContractTest {

    private static final String BASE_IRI = "https://smxplore.local/contracts/events/";
    private static final String EVENT_TYPE = "bookings.reservation.changed";
    private static final Path CONTRACTS = Path.of(
            System.getProperty("contracts.dir", "../../contracts/events"));

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ReservationEventFactory factory = new ReservationEventFactory(mapper);

    @Test
    void exampleMatchesTheV2Contract() throws Exception {
        JsonNode example = mapper.readTree(Files.readString(
                CONTRACTS.resolve("examples").resolve(EVENT_TYPE + ".v2.example.json")));

        Set<com.networknt.schema.ValidationMessage> errors = schema().validate(example);

        assertThat(errors).isEmpty();
    }

    @Test
    void generatedNoShowEventMatchesTheV2Contract() throws Exception {
        var reservation = Reservation.builder()
                .id(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .itemType(ReservationItemType.EVENT)
                .itemId(UUID.randomUUID())
                .itemName("Concierto")
                .providerId(UUID.randomUUID())
                .numberOfPeople(2)
                .total(new BigDecimal("200.00"))
                .paymentMethod(PaymentMethod.CASH)
                .status(ReservationStatus.NO_SHOW)
                .bookedFor(Instant.now().plusSeconds(3_600))
                .build();

        var event = factory.reservationChanged(reservation).block();

        assertThat(event).isNotNull();
        JsonNode envelope = mapper.readTree(event.payload());
        Set<com.networknt.schema.ValidationMessage> errors = schema().validate(envelope);

        assertThat(errors).isEmpty();
        assertThat(envelope.at("/payload/status").asText()).isEqualTo("NO_SHOW");
        assertThat(envelope.at("/payload/items/0/managerId").asText())
                .isEqualTo(reservation.getProviderId().toString());
    }

    private JsonSchema schema() throws Exception {
        Map<String, String> schemas = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(CONTRACTS)) {
            List<Path> files = paths
                    .filter(path -> path.getFileName().toString().endsWith(".v2.json"))
                    .sorted()
                    .toList();
            for (Path path : files) {
                schemas.put(BASE_IRI + path.getFileName(), Files.readString(path));
            }
        }
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaLoaders(loaders -> loaders.schemas(schemas)));
        String fileName = EVENT_TYPE + ".v2.json";
        return factory.getSchema(
                SchemaLocation.of(BASE_IRI + fileName),
                mapper.readTree(Files.readString(CONTRACTS.resolve(fileName))),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
    }
}
