package com.smxplore.sitesandservices.application.event;

import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.event.DomainEvent;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventFactoryTest {

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ZONE_ID = UUID.fromString("33333333-3333-3333-3333-333333333301");
    private static final UUID MANAGER_ID = UUID.fromString("66666666-6666-6666-6666-666666666601");

    private final DomainEventFactory factory = new DomainEventFactory("ms-04", CLOCK);

    @Test
    void zoneChangedPublishesTheContractFields() {
        Zone zone = zone()
                .description("Centro historico")
                .build();

        DomainEvent event = factory.zoneChanged(zone, "corr-zone");

        assertThat(event.eventType()).isEqualTo("catalog.zone.changed");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.aggregateId()).isEqualTo(ZONE_ID);
        assertThat(event.correlationId()).isEqualTo("corr-zone");
        assertThat(event.producer()).isEqualTo("ms-04");
        assertThat(event.occurredAt()).isEqualTo(NOW);
        assertThat(event.payload())
                .containsEntry("zoneId", ZONE_ID.toString())
                .containsEntry("managerId", MANAGER_ID.toString())
                .containsEntry("name", "Centro")
                .containsEntry("type", "HISTORIC")
                .containsEntry("description", "Centro historico")
                .containsEntry("status", "ACTIVE")
                .containsEntry("updatedAt", NOW.toString());
    }

    @Test
    void zonePayloadOmitsOptionalFieldsWhenTheZoneDoesNotHaveThem() {
        DomainEvent event = factory.zoneChanged(zone().type(null).build(), "corr-zone");

        assertThat(event.payload()).doesNotContainKeys("type", "description");
        assertThat(event.payload().get("zoneId")).isEqualTo(ZONE_ID.toString());
    }

    @Test
    void zoneChangedGeneratesACorrelationIdWhenTheCallerDoesNotProvideOne() {
        DomainEvent event = factory.zoneChanged(zone().build(), null);

        assertThat(event.correlationId()).isNotBlank();
        assertThat(UUID.fromString(event.correlationId())).isNotNull();
    }

    @Test
    void sitePayloadCarriesTheZoneIdNeededToLinkTheSiteToItsZone() {
        Site site = site();

        DomainEvent event = factory.siteChanged(site, "corr-site");

        assertThat(event.eventVersion()).isEqualTo(2);
        assertThat(event.payload())
                .containsEntry("zoneId", ZONE_ID.toString())
                .containsEntry("siteId", site.siteId().toString());
    }

    @Test
    void reviewWithoutResponseDoesNotPublishTheResponseField() {
        DomainEvent event = factory.reviewChanged(review(null, null), "corr-review");

        assertThat(event.payload()).doesNotContainKey("response");
    }

    @Test
    void reviewWithResponsePublishesTheResponseTextAndTimestamp() {
        Instant respondedAt = NOW.plusSeconds(600);

        DomainEvent event = factory.reviewChanged(review("Gracias por venir", respondedAt), "corr-review");

        assertThat(event.payload()).containsEntry(
                "response",
                Map.of("text", "Gracias por venir", "respondedAt", respondedAt.toString())
        );
    }

    private Zone.ZoneBuilder zone() {
        Zone.ZoneBuilder builder = Zone.builder()
                .zoneId(ZONE_ID)
                .managerId(MANAGER_ID)
                .name("Centro")
                .type("HISTORIC")
                .status(ZoneStatus.ACTIVE)
                .createdAt(NOW)
                .updatedAt(NOW);
        return builder;
    }

    private Site site() {
        return Site.builder()
                .siteId(UUID.fromString("22222222-2222-2222-2222-222222222201"))
                .managerId(MANAGER_ID)
                .name("Playa Rosa")
                .description("Sitio de prueba")
                .categoryId(UUID.fromString("11111111-1111-1111-1111-111111111102"))
                .categoryName("Playas")
                .zoneId(ZONE_ID)
                .zoneName("Centro")
                .zoneStatus(ZoneStatus.ACTIVE)
                .latitude(10.391)
                .longitude(-75.479)
                .photosUrls(List.of())
                .status(ContentStatus.PUBLISHED)
                .maxCapacity(100)
                .availability(List.of())
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private Review review(String response, Instant respondedAt) {
        return Review.builder()
                .reviewId(UUID.fromString("77777777-7777-7777-7777-777777777701"))
                .touristId(UUID.fromString("66666666-6666-6666-6666-666666666602"))
                .touristName("Ana")
                .itemType(ItemType.SITE)
                .itemId(UUID.fromString("22222222-2222-2222-2222-222222222201"))
                .itemName("Playa Rosa")
                .rating(5)
                .commentary("Excelente")
                .photosUrls(List.of())
                .response(response)
                .respondedAt(respondedAt)
                .helpfulReactions(0)
                .status(ReviewStatus.PUBLISHED)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
