package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.AvailabilitySlotDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.CategoryDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.ProviderSnapshotDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.ReviewDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.ServiceDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.SiteDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.SiteSnapshotDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.ZoneSnapshotDocument;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Component
public class MongoCatalogMapper {

    public CategoryDocument toDocument(Category category) {
        return CategoryDocument.builder()
                .id(category.categoryId().toString())
                .name(category.name())
                .scope(category.scope().name())
                .parentId(category.parentId() == null ? null : category.parentId().toString())
                .updatedAt(category.updatedAt())
                .build();
    }

    public Category toDomain(CategoryDocument document) {
        Category category = Category.builder()
                .categoryId(UUID.fromString(document.getId()))
                .name(document.getName())
                .scope(CategoryScope.valueOf(document.getScope()))
                .parentId(document.getParentId() == null ? null : UUID.fromString(document.getParentId()))
                .updatedAt(document.getUpdatedAt())
                .build();
        category.validate();
        return category;
    }

    public SiteDocument toDocument(Site site) {
        return SiteDocument.builder()
                .id(site.siteId().toString())
                .managerId(site.managerId().toString())
                .name(site.name())
                .description(site.description())
                .categoryId(site.categoryId().toString())
                .categoryName(site.categoryName())
                .zone(zoneSnapshot(site.zoneId(), site.zoneName(), site.zoneStatus()))
                .latitude(site.latitude())
                .longitude(site.longitude())
                .photosUrls(site.photosUrls())
                .status(site.status().name())
                .maxCapacity(site.maxCapacity())
                .availability(availabilityDocuments(site.availability()))
                .createdAt(site.createdAt())
                .updatedAt(site.updatedAt())
                .build();
    }

    public Site toDomain(SiteDocument document) {
        Site site = Site.builder()
                .siteId(UUID.fromString(document.getId()))
                .managerId(UUID.fromString(document.getManagerId()))
                .name(document.getName())
                .description(document.getDescription())
                .categoryId(UUID.fromString(document.getCategoryId()))
                .categoryName(document.getCategoryName())
                .zoneId(UUID.fromString(document.getZone().getId()))
                .zoneName(document.getZone().getName())
                .zoneStatus(ZoneStatus.valueOf(document.getZone().getStatus()))
                .latitude(document.getLatitude())
                .longitude(document.getLongitude())
                .photosUrls(document.getPhotosUrls())
                .status(ContentStatus.valueOf(document.getStatus()))
                .maxCapacity(document.getMaxCapacity())
                .availability(availabilityDomain(document.getAvailability()))
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
        site.validate();
        return site;
    }

    public ServiceDocument toDocument(Service service) {
        return ServiceDocument.builder()
                .id(service.serviceId().toString())
                .provider(providerSnapshot(service.providerId(), service.providerName()))
                .site(siteSnapshot(service.siteId(), service.siteName()))
                .zone(zoneSnapshot(service.zoneId(), service.zoneName(), service.zoneStatus()))
                .categoryId(service.categoryId().toString())
                .categoryName(service.categoryName())
                .name(service.name())
                .description(service.description())
                .type(service.type())
                .latitude(service.latitude())
                .longitude(service.longitude())
                .price(service.price())
                .status(service.status().name())
                .maxCapacity(service.maxCapacity())
                .availability(availabilityDocuments(service.availability()))
                .photosUrls(service.photosUrls())
                .cancellationPolicy(service.cancellationPolicy())
                .createdAt(service.createdAt())
                .updatedAt(service.updatedAt())
                .build();
    }

    public Service toDomain(ServiceDocument document) {
        Service service = Service.builder()
                .serviceId(UUID.fromString(document.getId()))
                .providerId(UUID.fromString(document.getProvider().getId()))
                .providerName(document.getProvider().getName())
                .siteId(UUID.fromString(document.getSite().getId()))
                .siteName(document.getSite().getName())
                .zoneId(UUID.fromString(document.getZone().getId()))
                .zoneName(document.getZone().getName())
                .zoneStatus(ZoneStatus.valueOf(document.getZone().getStatus()))
                .categoryId(UUID.fromString(document.getCategoryId()))
                .categoryName(document.getCategoryName())
                .name(document.getName())
                .description(document.getDescription())
                .type(document.getType())
                .latitude(document.getLatitude())
                .longitude(document.getLongitude())
                .price(document.getPrice())
                .status(ContentStatus.valueOf(document.getStatus()))
                .maxCapacity(document.getMaxCapacity())
                .availability(availabilityDomain(document.getAvailability()))
                .photosUrls(document.getPhotosUrls())
                .cancellationPolicy(document.getCancellationPolicy())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
        service.validate();
        return service;
    }

    public ReviewDocument toDocument(Review review) {
        return ReviewDocument.builder()
                .id(review.reviewId().toString())
                .touristId(review.touristId().toString())
                .touristName(review.touristName())
                .itemType(review.itemType().name())
                .itemId(review.itemId().toString())
                .itemName(review.itemName())
                .rating(review.rating())
                .commentary(review.commentary())
                .photosUrls(review.photosUrls())
                .response(review.response())
                .respondedAt(review.respondedAt())
                .helpfulReactions(review.helpfulReactions())
                .status(review.status().name())
                .createdAt(review.createdAt())
                .updatedAt(review.updatedAt())
                .build();
    }

    public Review toDomain(ReviewDocument document) {
        Review review = Review.builder()
                .reviewId(UUID.fromString(document.getId()))
                .touristId(UUID.fromString(document.getTouristId()))
                .touristName(document.getTouristName())
                .itemType(ItemType.valueOf(document.getItemType()))
                .itemId(UUID.fromString(document.getItemId()))
                .itemName(document.getItemName())
                .rating(document.getRating())
                .commentary(document.getCommentary())
                .photosUrls(document.getPhotosUrls())
                .response(document.getResponse())
                .respondedAt(document.getRespondedAt())
                .helpfulReactions(document.getHelpfulReactions())
                .status(ReviewStatus.valueOf(document.getStatus()))
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
        review.validate();
        return review;
    }

    private ZoneSnapshotDocument zoneSnapshot(UUID zoneId, String zoneName, ZoneStatus status) {
        return ZoneSnapshotDocument.builder()
                .id(zoneId.toString())
                .name(zoneName)
                .status(status.name())
                .build();
    }

    private ProviderSnapshotDocument providerSnapshot(UUID providerId, String providerName) {
        return ProviderSnapshotDocument.builder()
                .id(providerId.toString())
                .name(providerName)
                .build();
    }

    private SiteSnapshotDocument siteSnapshot(UUID siteId, String siteName) {
        return SiteSnapshotDocument.builder()
                .id(siteId.toString())
                .name(siteName)
                .build();
    }

    private List<AvailabilitySlotDocument> availabilityDocuments(List<AvailabilitySlot> availability) {
        return availability.stream()
                .map(slot -> AvailabilitySlotDocument.builder()
                        .dayOfWeek(slot.dayOfWeek().name())
                        .opensAt(slot.opensAt().toString())
                        .closesAt(slot.closesAt().toString())
                        .build())
                .toList();
    }

    private List<AvailabilitySlot> availabilityDomain(List<AvailabilitySlotDocument> availability) {
        if (availability == null) {
            return List.of();
        }
        return availability.stream()
                .map(slot -> new AvailabilitySlot(
                        DayOfWeek.valueOf(slot.getDayOfWeek()),
                        LocalTime.parse(slot.getOpensAt()),
                        LocalTime.parse(slot.getClosesAt())
                ))
                .toList();
    }
}
