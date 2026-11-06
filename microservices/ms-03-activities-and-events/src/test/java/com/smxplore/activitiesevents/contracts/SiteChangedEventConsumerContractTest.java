package com.smxplore.activitiesevents.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smxplore.activitiesevents.application.events.RefreshSiteSnapshotUseCase.SiteChanged;
import com.smxplore.activitiesevents.infrastructure.adapters.in.kafka.SiteChangedEventDecoder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato del lado consumidor de {@code catalog.site.changed}.
 *
 * <p>ms-03 no produce este evento: lo consume de ms-04 para refrescar el snapshot que guarda en
 * {@code evento} ({@code sitio_nombre}, {@code manager_id}). Por eso el test decodifica los fixtures
 * reales de {@code contracts/events/examples/} con el decoder de produccion, en vez de validar un
 * payload generado localmente. Fija ademas el comportamiento actual frente a v1 (se ignora) y frente a
 * un v2 sin {@code managerId} (se rechaza), que es lo que hace el consumidor en runtime.
 */
class SiteChangedEventConsumerContractTest {

    private static final String EVENT_TYPE = "catalog.site.changed";
    private static final Path CONTRACTS = Path.of(
            System.getProperty("contracts.dir", "../../contracts/events"));

    private final ObjectMapper mapper = new ObjectMapper();
    private final SiteChangedEventDecoder decoder = new SiteChangedEventDecoder(mapper);

    @Test
    void decodesTheV2ExamplePublishedByMs04() throws Exception {
        JsonNode example = example("v2");
        JsonNode payload = example.path("payload");

        Optional<SiteChanged> decoded = decoder.decode(mapper.writeValueAsString(example));

        assertThat(example.path("eventVersion").asInt()).isEqualTo(2);
        assertThat(decoded).isPresent();
        SiteChanged change = decoded.orElseThrow();
        assertThat(change.siteId()).isEqualTo(UUID.fromString(payload.path("siteId").asText()));
        assertThat(change.managerId()).isEqualTo(UUID.fromString(payload.path("managerId").asText()));
        assertThat(change.siteName()).isEqualTo(payload.path("name").asText());
        assertThat(change.updatedAt()).isEqualTo(Instant.parse(payload.path("updatedAt").asText()));
        // El consumidor solo usa siteId/name/managerId/updatedAt: categoryId, latitude, longitude y
        // status viajan en el contrato pero no deben romper el decode.
        assertThat(payload.hasNonNull("categoryId")).isTrue();
        assertThat(payload.hasNonNull("latitude")).isTrue();
    }

    @Test
    void ignoresTheV1ExampleBecauseItHasNoManager() throws Exception {
        JsonNode example = example("v1");

        assertThat(example.path("eventVersion").asInt()).isEqualTo(1);
        assertThat(example.path("payload").has("managerId"))
                .as("v1 no lleva managerId: si el fixture cambia, esta premisa deja de valer")
                .isFalse();

        assertThat(decoder.decode(mapper.writeValueAsString(example))).isEmpty();
    }

    @Test
    void ignoresAV1ThatCarriesAManagerBecauseTheVersionGateComesFirst() throws Exception {
        ObjectNode example = (ObjectNode) example("v1");
        ((ObjectNode) example.path("payload")).put("managerId", UUID.randomUUID().toString());

        // El consumidor corta por version, no por presencia del campo: mientras ms-04 publique v1 el
        // snapshot del manager no se toca (ver SiteChangedEventDecoder).
        assertThat(decoder.decode(mapper.writeValueAsString(example))).isEmpty();
    }

    @Test
    void rejectsAV2PayloadWithoutManagerId() throws Exception {
        ObjectNode example = (ObjectNode) example("v2");
        ((ObjectNode) example.path("payload")).remove("managerId");

        // Se rechaza con IllegalArgumentException: SiteConsumerConfiguration la marca como no
        // reintentable y el mensaje viaja a catalog.site.changed.DLT.
        assertThatThrownBy(() -> decoder.decode(mapper.writeValueAsString(example)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("managerId is required in " + EVENT_TYPE);
    }

    private JsonNode example(String version) throws Exception {
        Path file = CONTRACTS.resolve("examples").resolve(EVENT_TYPE + "." + version + ".example.json");
        assertThat(file).as("fixture del contrato del consumidor").exists();
        return mapper.readTree(Files.readString(file));
    }
}
