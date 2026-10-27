package com.smxplore.sitesandservices.application.support;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.CatalogTarget;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.ports.out.CategoryRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ZoneRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class CatalogLookup {

    private final CategoryRepositoryPort categoryRepository;
    private final ZoneRepositoryPort zoneRepository;
    private final SiteRepositoryPort siteRepository;
    private final ServiceRepositoryPort serviceRepository;
    private final ReviewRepositoryPort reviewRepository;

    public Mono<Category> category(UUID categoryId) {
        if (categoryId == null) {
            return Mono.error(new InvalidCatalogException("categoryId is required"));
        }
        return categoryRepository.findById(categoryId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Category", categoryId)));
    }

    public Mono<Zone> zone(UUID zoneId) {
        if (zoneId == null) {
            return Mono.error(new InvalidCatalogException("zoneId is required"));
        }
        return zoneRepository.findById(zoneId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Zone", zoneId)));
    }

    public Mono<Site> site(UUID siteId) {
        if (siteId == null) {
            return Mono.error(new InvalidCatalogException("siteId is required"));
        }
        return siteRepository.findById(siteId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Site", siteId)));
    }

    public Mono<Service> service(UUID serviceId) {
        if (serviceId == null) {
            return Mono.error(new InvalidCatalogException("serviceId is required"));
        }
        return serviceRepository.findById(serviceId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Service", serviceId)));
    }

    public Mono<Review> review(UUID reviewId) {
        if (reviewId == null) {
            return Mono.error(new InvalidCatalogException("reviewId is required"));
        }
        return reviewRepository.findById(reviewId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Review", reviewId)));
    }

    public Mono<SiteContext> siteContext(UUID categoryId, UUID zoneId) {
        return Mono.zip(category(categoryId), zone(zoneId))
                .map(tuple -> new SiteContext(tuple.getT1(), tuple.getT2()));
    }

    public Mono<ServiceContext> serviceContext(UUID siteId, UUID categoryId, UUID zoneId) {
        return Mono.zip(site(siteId), category(categoryId), zone(zoneId))
                .map(tuple -> new ServiceContext(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    public Mono<CatalogTarget> reviewTarget(ItemType itemType, UUID itemId) {
        if (itemType == null) {
            return Mono.error(new InvalidCatalogException("review itemType is required"));
        }
        if (itemId == null) {
            return Mono.error(new InvalidCatalogException("review itemId is required"));
        }
        return switch (itemType) {
            case SITE -> site(itemId).map(this::siteTarget);
            case SERVICE -> service(itemId).map(this::serviceTarget);
            default -> Mono.error(new InvalidCatalogException(
                    "ms-04 only supports reviews for SITE and SERVICE"));
        };
    }

    public Mono<CatalogTarget> publishedReviewTarget(ItemType itemType, UUID itemId) {
        return reviewTarget(itemType, itemId)
                .flatMap(this::ensurePublished);
    }

    private Mono<CatalogTarget> ensurePublished(CatalogTarget target) {
        return target.status().isPublished()
                ? Mono.just(target)
                : Mono.error(new InvalidCatalogException("Only published items can be reviewed"));
    }

    private CatalogTarget siteTarget(Site site) {
        return new CatalogTarget(
                ItemType.SITE,
                site.siteId(),
                site.name(),
                site.managerId(),
                site.status()
        );
    }

    private CatalogTarget serviceTarget(Service service) {
        return new CatalogTarget(
                ItemType.SERVICE,
                service.serviceId(),
                service.name(),
                service.providerId(),
                service.status()
        );
    }

    public record SiteContext(Category category, Zone zone) {
    }

    public record ServiceContext(Site site, Category category, Zone zone) {
    }
}
