package com.smxplore.activitiesevents.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fija la tabla canonica de estados del contrato (P3-2, opcion A firmada por el PL).
 *
 * <p>El contrato es la fuente de verdad de los literales que viajan por Kafka. Este test falla si
 * un enum de ms-03 deja de coincidir con el schema correspondiente, que es exactamente el momento
 * en el que un consumidor (ms-04, ms-05) empezaria a recibir un estado que no entiende.
 *
 * <p>{@code contracts/README.md} documenta las tablas; este test las hace ejecutables.
 */
class CatalogStateContractTest {

    private static final Path CONTRACTS = Path.of(
            System.getProperty("contracts.dir", "../../contracts/events"));

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void activityStatusMatchesTheV2Contract() throws Exception {
        assertThat(names(ActivityStatus.values()))
                .containsExactlyElementsOf(literals("activities.activity.changed.v2.json"));
    }

    @Test
    void eventStatusMatchesTheV2Contract() throws Exception {
        assertThat(names(EventStatus.values()))
                .containsExactlyElementsOf(literals("activities.event.changed.v2.json"));
    }

    @Test
    void reviewStatusMatchesTheV1Contract() throws Exception {
        assertThat(names(ReviewStatus.values()))
                .containsExactlyElementsOf(literals("reviews.review.changed.v1.json"));
    }

    @Test
    void everyReservationStateThatMs03EmitsIsRepresentableInV2() throws Exception {
        Set<String> v2 = new LinkedHashSet<>(literals("bookings.reservation.changed.v2.json"));

        assertThat(v2).containsAll(names(ReservationStatus.values()));
        assertThat(v2).contains("NO_SHOW");
        // El schema v2 admite PENDING como transicion externa, pero ms-03 nace CONFIRMED y nunca lo
        // emite; la tabla de contracts/README.md lo deja escrito.
        assertThat(v2).contains("PENDING");
        assertThat(names(ReservationStatus.values())).doesNotContain("PENDING");
    }

    @Test
    void noShowOnlyExistsInV2() throws Exception {
        Set<String> v1 = new LinkedHashSet<>(literals("bookings.reservation.changed.v1.json"));
        Set<String> v2 = new LinkedHashSet<>(literals("bookings.reservation.changed.v2.json"));

        assertThat(v1).doesNotContain("NO_SHOW");
        assertThat(v1).isSubsetOf(v2);
    }

    private List<String> literals(String schemaFile) throws Exception {
        JsonNode root = mapper.readTree(Files.readString(CONTRACTS.resolve(schemaFile)));
        for (JsonNode node : root.path("allOf")) {
            JsonNode literals = node.path("properties").path("payload")
                    .path("properties").path("status").path("enum");
            if (literals.isArray() && !literals.isEmpty()) {
                List<String> values = new java.util.ArrayList<>();
                literals.forEach(value -> values.add(value.asText()));
                return values;
            }
        }
        throw new AssertionError("no encontre el enum de status en " + schemaFile);
    }

    private static List<String> names(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(Enum::name).toList();
    }
}
