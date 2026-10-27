package com.smxplore.sitesandservices.domain;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelValidationTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Test
    void rejectsAnAvailabilitySlotWithAnInvalidRange() {
        assertThatThrownBy(() -> new AvailabilitySlot(
                DayOfWeek.MONDAY,
                LocalTime.of(18, 0),
                LocalTime.of(9, 0)
        )).isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("closesAt");
    }

    @Test
    void rejectsAnInvalidCategoryParent() {
        UUID categoryId = UUID.randomUUID();
        Category category = Category.builder()
                .categoryId(categoryId)
                .name("Cultura")
                .scope(CategoryScope.GENERAL)
                .parentId(categoryId)
                .updatedAt(NOW)
                .build();

        assertThatThrownBy(category::validate)
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("own parent");
    }

    @Test
    void rejectsInvalidSiteCoordinates() {
        Site site = Site.builder()
                .siteId(UUID.randomUUID())
                .managerId(UUID.randomUUID())
                .name("Sitio")
                .description("Descripción")
                .categoryId(UUID.randomUUID())
                .categoryName("Categoría")
                .zoneId(UUID.randomUUID())
                .zoneName("Zona")
                .zoneStatus(ZoneStatus.ACTIVE)
                .latitude(100.0)
                .longitude(-75.0)
                .photosUrls(List.of())
                .status(ContentStatus.PUBLISHED)
                .maxCapacity(10)
                .availability(List.of())
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        assertThatThrownBy(site::validate)
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("latitude");
    }

    @Test
    void rejectsAZoneWithInvalidChronology() {
        Zone zone = Zone.builder()
                .zoneId(UUID.randomUUID())
                .managerId(UUID.randomUUID())
                .name("Zona")
                .type("URBAN")
                .status(ZoneStatus.ACTIVE)
                .createdAt(NOW)
                .updatedAt(NOW.minusSeconds(1))
                .build();

        assertThatThrownBy(zone::validate)
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("updatedAt");
    }

    @Test
    void rejectsNegativePricesAndRatingsOutsideTheAllowedRange() {
        Service service = Service.builder()
                .serviceId(UUID.randomUUID())
                .providerId(UUID.randomUUID())
                .providerName("Prestadora")
                .siteId(UUID.randomUUID())
                .siteName("Sitio")
                .zoneId(UUID.randomUUID())
                .zoneName("Zona")
                .zoneStatus(ZoneStatus.ACTIVE)
                .categoryId(UUID.randomUUID())
                .categoryName("Categoría")
                .name("Servicio")
                .type("RESTAURANT")
                .latitude(10.0)
                .longitude(-75.0)
                .price(new BigDecimal("-1"))
                .status(ContentStatus.PUBLISHED)
                .maxCapacity(10)
                .availability(List.of())
                .photosUrls(List.of())
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        assertThatThrownBy(service::validate)
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("price");

        Review review = Review.builder()
                .reviewId(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .touristName("Turista")
                .itemType(ItemType.SITE)
                .itemId(UUID.randomUUID())
                .itemName("Sitio")
                .rating(6)
                .photosUrls(List.of())
                .status(ReviewStatus.PUBLISHED)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        assertThatThrownBy(review::validate)
                .isInstanceOf(InvalidCatalogException.class)
                .hasMessageContaining("rating");
    }
}
