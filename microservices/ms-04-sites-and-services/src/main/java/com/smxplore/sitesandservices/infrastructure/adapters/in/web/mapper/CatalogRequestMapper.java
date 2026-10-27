package com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category.CreateCategoryRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category.UpdateCategoryRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.AvailabilitySlotRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ChangeReviewStatusRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.CreateReviewRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.RespondReviewRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service.CreateServiceRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service.UpdateServiceRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site.CreateSiteRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site.UpdateSiteRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone.CreateZoneRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone.UpdateZoneRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CatalogRequestMapper {

    public Category toDomain(CreateCategoryRequest request) {
        return Category.builder()
                .name(request.name())
                .scope(request.scope())
                .parentId(request.parentId())
                .build();
    }

    public Category toDomain(UUID categoryId, UpdateCategoryRequest request) {
        return Category.builder()
                .categoryId(categoryId)
                .name(request.name())
                .scope(request.scope())
                .parentId(request.parentId())
                .build();
    }

    public Zone toDomain(CreateZoneRequest request) {
        Zone.ZoneBuilder builder = Zone.builder()
                .name(request.name())
                .type(request.type())
                .description(request.description());
        if (request.status() != null) {
            builder.status(request.status());
        }
        return builder.build();
    }

    public Zone toDomain(UUID zoneId, UpdateZoneRequest request) {
        return Zone.builder()
                .zoneId(zoneId)
                .name(request.name())
                .type(request.type())
                .description(request.description())
                .build();
    }

    public Zone toZoneStatus(UUID zoneId, ZoneStatus status) {
        return Zone.builder()
                .zoneId(zoneId)
                .status(status)
                .build();
    }

    public Site toDomain(CreateSiteRequest request) {
        Site.SiteBuilder builder = Site.builder()
                .name(request.name())
                .description(request.description())
                .categoryId(request.categoryId())
                .zoneId(request.zoneId())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .maxCapacity(request.maxCapacity());
        if (request.photosUrls() != null) {
            builder.photosUrls(request.photosUrls());
        }
        if (request.availability() != null) {
            builder.availability(availability(request.availability()));
        }
        if (request.status() != null) {
            builder.status(request.status());
        }
        return builder.build();
    }

    public Site toDomain(UUID siteId, UpdateSiteRequest request) {
        Site.SiteBuilder builder = Site.builder()
                .siteId(siteId)
                .name(request.name())
                .description(request.description())
                .categoryId(request.categoryId())
                .zoneId(request.zoneId())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .maxCapacity(request.maxCapacity());
        if (request.photosUrls() != null) {
            builder.photosUrls(request.photosUrls());
        }
        if (request.availability() != null) {
            builder.availability(availability(request.availability()));
        }
        return builder.build();
    }

    public Site toSiteStatus(UUID siteId, ContentStatus status) {
        return Site.builder()
                .siteId(siteId)
                .status(status)
                .build();
    }

    public Site toSiteZone(UUID siteId, UUID zoneId) {
        return Site.builder()
                .siteId(siteId)
                .zoneId(zoneId)
                .build();
    }

    public Service toDomain(CreateServiceRequest request) {
        Service.ServiceBuilder builder = Service.builder()
                .name(request.name())
                .description(request.description())
                .type(request.type())
                .categoryId(request.categoryId())
                .siteId(request.siteId())
                .zoneId(request.zoneId())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .price(request.price())
                .maxCapacity(request.maxCapacity())
                .cancellationPolicy(request.cancellationPolicy());
        if (request.availability() != null) {
            builder.availability(availability(request.availability()));
        }
        if (request.photosUrls() != null) {
            builder.photosUrls(request.photosUrls());
        }
        if (request.status() != null) {
            builder.status(request.status());
        }
        return builder.build();
    }

    public Service toDomain(UUID serviceId, UpdateServiceRequest request) {
        Service.ServiceBuilder builder = Service.builder()
                .serviceId(serviceId)
                .name(request.name())
                .description(request.description())
                .type(request.type())
                .categoryId(request.categoryId())
                .siteId(request.siteId())
                .zoneId(request.zoneId())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .price(request.price())
                .maxCapacity(request.maxCapacity())
                .cancellationPolicy(request.cancellationPolicy());
        if (request.availability() != null) {
            builder.availability(availability(request.availability()));
        }
        if (request.photosUrls() != null) {
            builder.photosUrls(request.photosUrls());
        }
        return builder.build();
    }

    public Service toServiceStatus(UUID serviceId, ContentStatus status) {
        return Service.builder()
                .serviceId(serviceId)
                .status(status)
                .build();
    }

    public Review toDomain(CreateReviewRequest request) {
        Review.ReviewBuilder builder = Review.builder()
                .itemType(request.itemType())
                .itemId(request.itemId())
                .rating(request.rating())
                .commentary(request.commentary());
        if (request.photosUrls() != null) {
            builder.photosUrls(request.photosUrls());
        }
        return builder.build();
    }

    public Review toReviewResponse(UUID reviewId, RespondReviewRequest request) {
        return Review.builder()
                .reviewId(reviewId)
                .response(request.response())
                .build();
    }

    public Review toReviewStatus(UUID reviewId, ChangeReviewStatusRequest request) {
        return Review.builder()
                .reviewId(reviewId)
                .status(request.status())
                .build();
    }

    private List<AvailabilitySlot> availability(List<AvailabilitySlotRequest> slots) {
        return slots.stream()
                .map(slot -> new AvailabilitySlot(slot.dayOfWeek(), slot.opensAt(), slot.closesAt()))
                .toList();
    }
}
