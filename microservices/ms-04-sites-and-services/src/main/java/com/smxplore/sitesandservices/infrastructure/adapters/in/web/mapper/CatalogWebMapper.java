package com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper;

import com.smxplore.sitesandservices.domain.model.catalog.CatalogExportItem;
import com.smxplore.sitesandservices.domain.model.catalog.CatalogExportPage;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.review.ReputationMetrics;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewReport;
import com.smxplore.sitesandservices.domain.model.review.ReviewSummary;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.catalog.CatalogExportItemResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.catalog.CatalogExportPageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category.CategoryResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.AvailabilitySlotResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReputationResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReviewReportResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReviewResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.review.ReviewSummaryResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.service.ServiceResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.site.SiteResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.zone.ZoneResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CatalogWebMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.categoryId(),
                category.name(),
                category.scope(),
                category.parentId(),
                category.updatedAt()
        );
    }

    public ZoneResponse toResponse(Zone zone) {
        return new ZoneResponse(
                zone.zoneId(),
                zone.managerId(),
                zone.name(),
                zone.type(),
                zone.description(),
                zone.status(),
                zone.createdAt(),
                zone.updatedAt()
        );
    }

    public SiteResponse toResponse(Site site) {
        return new SiteResponse(
                site.siteId(),
                site.managerId(),
                site.name(),
                site.description(),
                site.categoryId(),
                site.categoryName(),
                site.zoneId(),
                site.zoneName(),
                site.zoneStatus(),
                site.latitude(),
                site.longitude(),
                site.photosUrls(),
                site.status(),
                site.maxCapacity(),
                availability(site.availability()),
                site.createdAt(),
                site.updatedAt()
        );
    }

    public ServiceResponse toResponse(Service service) {
        return new ServiceResponse(
                service.serviceId(),
                service.providerId(),
                service.providerName(),
                service.siteId(),
                service.siteName(),
                service.zoneId(),
                service.zoneName(),
                service.zoneStatus(),
                service.categoryId(),
                service.categoryName(),
                service.name(),
                service.description(),
                service.type(),
                service.latitude(),
                service.longitude(),
                service.price(),
                service.status(),
                service.maxCapacity(),
                availability(service.availability()),
                service.photosUrls(),
                service.cancellationPolicy(),
                service.createdAt(),
                service.updatedAt()
        );
    }

    public ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.reviewId(),
                review.touristId(),
                review.touristName(),
                review.itemType(),
                review.itemId(),
                review.itemName(),
                review.rating(),
                review.commentary(),
                review.photosUrls(),
                review.response(),
                review.respondedAt(),
                review.helpfulReactions(),
                review.status(),
                review.createdAt(),
                review.updatedAt()
        );
    }

    public ReviewSummaryResponse toResponse(ReviewSummary summary) {
        return new ReviewSummaryResponse(
                summary.itemType(),
                summary.itemId(),
                summary.averageRating(),
                summary.reviewCount()
        );
    }

    public ReviewReportResponse toResponse(ReviewReport report) {
        return new ReviewReportResponse(
                report.reportId(),
                report.reviewId(),
                report.itemType(),
                report.itemId(),
                report.reportedBy(),
                report.reportedByName(),
                report.reason(),
                report.details(),
                report.status(),
                report.createdAt(),
                report.resolvedAt(),
                report.resolvedBy(),
                report.resolution()
        );
    }

    public ReputationResponse toResponse(ReputationMetrics metrics) {
        return new ReputationResponse(
                metrics.itemType(),
                metrics.itemId(),
                metrics.averageRating(),
                metrics.reviewCount(),
                metrics.responseCount(),
                metrics.responseRate(),
                metrics.hiddenCount(),
                metrics.ratingDistribution(),
                metrics.lastReviewAt()
        );
    }

    public AvailabilitySlotResponse toResponse(AvailabilitySlot slot) {
        return new AvailabilitySlotResponse(
                slot.dayOfWeek(),
                slot.opensAt(),
                slot.closesAt(),
                slot.capacity()
        );
    }

    public CatalogExportItemResponse toResponse(CatalogExportItem item) {
        return new CatalogExportItemResponse(
                item.eventType(),
                item.aggregateId(),
                item.updatedAt(),
                item.payload()
        );
    }

    public CatalogExportPageResponse toResponse(CatalogExportPage page) {
        return new CatalogExportPageResponse(
                page.content().stream().map(this::toResponse).toList(),
                page.page(),
                page.size(),
                page.total(),
                page.hasNext(),
                page.updatedSince()
        );
    }

    private List<AvailabilitySlotResponse> availability(List<AvailabilitySlot> availability) {
        return availability.stream().map(this::toResponse).toList();
    }
}
