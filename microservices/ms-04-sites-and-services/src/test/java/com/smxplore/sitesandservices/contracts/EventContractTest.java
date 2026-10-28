package com.smxplore.sitesandservices.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.event.DomainEvent;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationItem;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.infrastructure.adapters.out.event.DomainEventJsonSerializer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EventContractTest {

    private static final String BASE_IRI = "https://smxplore.local/contracts/events/";
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final ObjectMapper schemaMapper = new ObjectMapper();
    private final tools.jackson.databind.ObjectMapper jsonMapper =
            new tools.jackson.databind.ObjectMapper();
    private final DomainEventJsonSerializer serializer =
            new DomainEventJsonSerializer(jsonMapper);
    private final DomainEventFactory factory = new DomainEventFactory("ms-04", CLOCK);
    private final Path contractsDirectory =
            Path.of(System.getProperty("contracts.dir", "../../contracts/events"));

    @Test
    void generatedEventsMatchTheirJsonSchemas() throws Exception {
        Map<String, JsonSchema> schemas = schemas();
        Map<String, DomainEvent> events = Map.of(
                "catalog.category.changed", factory.categoryChanged(category(), "corr-1"),
                "catalog.site.changed", factory.siteChanged(site(), "corr-2"),
                "catalog.service.changed", factory.serviceChanged(service(), "corr-3"),
                "reviews.review.changed", factory.reviewChanged(review(), "corr-4"),
                "bookings.reservation.changed", factory.reservationChanged(reservation(), "corr-5")
        );

        for (Map.Entry<String, DomainEvent> entry : events.entrySet()) {
            JsonNode json = readJson(serializer.serialize(entry.getValue()));
            Set<ValidationMessage> errors = schemas.get(entry.getKey()).validate(json);
            assertThat(errors)
                    .as("Contract violations for %s", entry.getKey())
                    .isEmpty();
            assertThat(json.get("producer").asText()).isEqualTo("ms-04");
            assertThat(json.get("eventVersion").asInt()).isEqualTo(1);
            assertThat(json.get("eventType").asText()).isEqualTo(entry.getKey());
        }
    }

    @Test
    void contractCatalogContainsTheEventsPublishedByMs04() throws Exception {
        Set<String> eventTypes;
        try (Stream<Path> paths = Files.list(contractsDirectory)) {
            eventTypes = paths
                    .filter(path -> path.getFileName().toString().endsWith(".v1.json"))
                    .filter(path -> !path.getFileName().toString().equals("envelope.v1.json"))
                    .map(path -> path.getFileName().toString()
                            .substring(0, path.getFileName().toString().length() - ".v1.json".length()))
                    .collect(Collectors.toSet());
        }

        assertThat(eventTypes).contains(
                "catalog.category.changed",
                "catalog.site.changed",
                "catalog.service.changed",
                "reviews.review.changed",
                "bookings.reservation.changed"
        );
    }

    private Map<String, JsonSchema> schemas() throws Exception {
        Map<String, String> schemaTexts = new java.util.LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(contractsDirectory)) {
            List<Path> files = paths
                    .filter(path -> path.getFileName().toString().endsWith(".v1.json"))
                    .sorted()
                    .toList();
            for (Path path : files) {
                schemaTexts.put(BASE_IRI + path.getFileName(), Files.readString(path));
            }
        }
        JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaLoaders(loaders -> loaders.schemas(schemaTexts)));
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true)
                .build();
        Map<String, JsonSchema> schemas = new java.util.LinkedHashMap<>();
        for (String eventType : List.of(
                "catalog.category.changed",
                "catalog.site.changed",
                "catalog.service.changed",
                "reviews.review.changed",
                "bookings.reservation.changed")) {
            String fileName = eventType + ".v1.json";
            schemas.put(eventType, schemaFactory.getSchema(
                    SchemaLocation.of(BASE_IRI + fileName),
                    schemaMapper.readTree(Files.readString(contractsDirectory.resolve(fileName))),
                    config
            ));
        }
        return schemas;
    }

    private JsonNode readJson(String json) throws Exception {
        return schemaMapper.readTree(json);
    }

    private Category category() {
        UUID categoryId = UUID.fromString("11111111-1111-1111-1111-111111111106");
        Category category = Category.builder()
                .categoryId(categoryId)
                .name("Música")
                .scope(CategoryScope.EVENT)
                .parentId(UUID.fromString("11111111-1111-1111-1111-111111111105"))
                .updatedAt(NOW)
                .build();
        category.validate();
        return category;
    }

    private Site site() {
        Site site = Site.builder()
                .siteId(UUID.fromString("22222222-2222-2222-2222-222222222201"))
                .managerId(UUID.fromString("66666666-6666-6666-6666-666666666601"))
                .name("Playa Rosa")
                .description("Sitio de prueba")
                .categoryId(UUID.fromString("11111111-1111-1111-1111-111111111102"))
                .categoryName("Playas")
                .zoneId(UUID.fromString("33333333-3333-3333-3333-333333333301"))
                .zoneName("Centro")
                .zoneStatus(ZoneStatus.ACTIVE)
                .latitude(10.391)
                .longitude(-75.479)
                .photosUrls(List.of("https://example.com/site.jpg"))
                .status(ContentStatus.PUBLISHED)
                .maxCapacity(100)
                .availability(List.of())
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
        site.validate();
        return site;
    }

    private Service service() {
        Service service = Service.builder()
                .serviceId(UUID.fromString("55555555-5555-5555-5555-555555555501"))
                .providerId(UUID.fromString("66666666-6666-6666-6666-666666666602"))
                .providerName("Prestadora de prueba")
                .siteId(UUID.fromString("22222222-2222-2222-2222-222222222201"))
                .siteName("Playa Rosa")
                .zoneId(UUID.fromString("33333333-3333-3333-3333-333333333301"))
                .zoneName("Centro")
                .zoneStatus(ZoneStatus.ACTIVE)
                .categoryId(UUID.fromString("11111111-1111-1111-1111-111111111107"))
                .categoryName("Gastronomía")
                .name("Restaurante del Mar")
                .description("Servicio de prueba")
                .type("RESTAURANT")
                .latitude(10.391)
                .longitude(-75.479)
                .price(new BigDecimal("50000.00"))
                .status(ContentStatus.PUBLISHED)
                .maxCapacity(20)
                .availability(List.of())
                .photosUrls(List.of())
                .cancellationPolicy("48 horas")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
        service.validate();
        return service;
    }

    private Reservation reservation() {
        ReservationItem item = ReservationItem.builder()
                .reservationItemId(UUID.fromString("88888888-8888-8888-8888-888888888801"))
                .serviceId(UUID.fromString("55555555-5555-5555-5555-555555555501"))
                .serviceName("Restaurante del Mar")
                .providerId(UUID.fromString("66666666-6666-6666-6666-666666666602"))
                .providerName("Prestadora de prueba")
                .serviceType("RESTAURANT")
                .unitPrice(new BigDecimal("50000.00"))
                .people(2)
                .total(new BigDecimal("100000.00"))
                .position(0)
                .build();
        Reservation reservation = Reservation.builder()
                .reservationId(UUID.fromString("99999999-9999-9999-9999-999999999901"))
                .touristId(UUID.fromString("66666666-6666-6666-6666-666666666601"))
                .touristName("Ana")
                .status(ReservationStatus.CONFIRMED)
                .total(new BigDecimal("100000.00"))
                .bookedFor(NOW.plusSeconds(3600))
                .notes("Mesa junto a la ventana")
                .items(List.of(item))
                .createdAt(NOW)
                .updatedAt(NOW)
                .confirmedAt(NOW)
                .build();
        reservation.validate();
        return reservation;
    }

    private Review review() {
        Review review = Review.builder()
                .reviewId(UUID.fromString("77777777-7777-7777-7777-777777777701"))
                .touristId(UUID.fromString("66666666-6666-6666-6666-666666666601"))
                .touristName("Ana")
                .itemType(ItemType.SITE)
                .itemId(UUID.fromString("22222222-2222-2222-2222-222222222201"))
                .itemName("Playa Rosa")
                .rating(5)
                .commentary("Excelente")
                .photosUrls(List.of())
                .helpfulReactions(0)
                .status(ReviewStatus.PUBLISHED)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
        review.validate();
        return review;
    }
}
