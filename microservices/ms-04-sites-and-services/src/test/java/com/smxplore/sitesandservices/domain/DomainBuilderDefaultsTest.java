package com.smxplore.sitesandservices.domain;

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
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainBuilderDefaultsTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Test
    void siteBuilderUsesDefaultStatusCapacityAndCollections() {
        Site site = validSiteBuilder().build();
        site.validate();

        assertThat(site.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(site.maxCapacity()).isEqualTo(1);
        assertThat(site.photosUrls()).isEmpty();
        assertThat(site.availability()).isEmpty();
    }

    @Test
    void zoneAndServiceBuildersUseDefaultStatus() {
        Zone zone = Zone.builder()
                .zoneId(UUID.randomUUID())
                .managerId(UUID.randomUUID())
                .name("Zona")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
        zone.validate();
        assertThat(zone.status()).isEqualTo(ZoneStatus.ACTIVE);

        Service service = validServiceBuilder().build();
        service.validate();
        assertThat(service.status()).isEqualTo(ContentStatus.DRAFT);
        assertThat(service.price()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(service.maxCapacity()).isEqualTo(1);
        assertThat(service.photosUrls()).isEmpty();
        assertThat(service.availability()).isEmpty();
    }

    @Test
    void reviewBuilderUsesPublishedStatusAndZeroReactions() {
        Review review = Review.builder()
                .reviewId(UUID.randomUUID())
                .touristId(UUID.randomUUID())
                .touristName("Turista")
                .itemType(ItemType.SITE)
                .itemId(UUID.randomUUID())
                .itemName("Sitio")
                .rating(5)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
        review.validate();

        assertThat(review.status()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(review.helpfulReactions()).isZero();
        assertThat(review.photosUrls()).isEmpty();
    }

    private Site.SiteBuilder validSiteBuilder() {
        return Site.builder()
                .siteId(UUID.randomUUID())
                .managerId(UUID.randomUUID())
                .name("Sitio")
                .categoryId(UUID.randomUUID())
                .categoryName("Categoría")
                .zoneId(UUID.randomUUID())
                .zoneName("Zona")
                .zoneStatus(ZoneStatus.ACTIVE)
                .latitude(10.0)
                .longitude(-75.0)
                .createdAt(NOW)
                .updatedAt(NOW);
    }

    private Service.ServiceBuilder validServiceBuilder() {
        return Service.builder()
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
                .createdAt(NOW)
                .updatedAt(NOW);
    }
}
