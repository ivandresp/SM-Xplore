package com.smxplore.sitesandservices.infrastructure.config;

import com.smxplore.sitesandservices.application.catalog.CategoryCreateService;
import com.smxplore.sitesandservices.application.catalog.CategoryGetService;
import com.smxplore.sitesandservices.application.catalog.CategoryListService;
import com.smxplore.sitesandservices.application.catalog.CategoryUpdateService;
import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.review.ReviewChangeStatusService;
import com.smxplore.sitesandservices.application.review.ReviewCreateService;
import com.smxplore.sitesandservices.application.review.ReviewGetService;
import com.smxplore.sitesandservices.application.review.ReviewListService;
import com.smxplore.sitesandservices.application.review.ReviewRespondService;
import com.smxplore.sitesandservices.application.review.ReviewSummaryService;
import com.smxplore.sitesandservices.application.service.ServiceChangeStatusService;
import com.smxplore.sitesandservices.application.service.ServiceCreateService;
import com.smxplore.sitesandservices.application.service.ServiceGetService;
import com.smxplore.sitesandservices.application.service.ServiceListService;
import com.smxplore.sitesandservices.application.service.ServiceUpdateService;
import com.smxplore.sitesandservices.application.site.SiteAssignZoneService;
import com.smxplore.sitesandservices.application.site.SiteChangeStatusService;
import com.smxplore.sitesandservices.application.site.SiteCreateService;
import com.smxplore.sitesandservices.application.site.SiteGetService;
import com.smxplore.sitesandservices.application.site.SiteListService;
import com.smxplore.sitesandservices.application.site.SiteUpdateService;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.application.zone.ZoneChangeStatusService;
import com.smxplore.sitesandservices.application.zone.ZoneCreateService;
import com.smxplore.sitesandservices.application.zone.ZoneGetService;
import com.smxplore.sitesandservices.application.zone.ZoneListService;
import com.smxplore.sitesandservices.application.zone.ZoneUpdateService;
import com.smxplore.sitesandservices.domain.ports.in.AssignSiteZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ChangeReviewStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ChangeServiceStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ChangeSiteStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ChangeZoneStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateCategoryUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateServiceUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateSiteUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetCategoryUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetReviewSummaryUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetServiceUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetSiteUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListCategoriesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListReviewsUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListServicesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListSitesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListZonesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.RespondToReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateCategoryUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateServiceUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateSiteUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.out.CategoryRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.OutboxPort;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.TransactionPort;
import com.smxplore.sitesandservices.domain.ports.out.ZoneRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class BeanConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public DomainEventFactory domainEventFactory(OutboxProperties properties, Clock clock) {
        return new DomainEventFactory(properties.getProducer(), clock);
    }

    @Bean
    public CatalogLookup catalogLookup(
            CategoryRepositoryPort categoryRepository,
            ZoneRepositoryPort zoneRepository,
            SiteRepositoryPort siteRepository,
            ServiceRepositoryPort serviceRepository,
            ReviewRepositoryPort reviewRepository
    ) {
        return new CatalogLookup(
                categoryRepository,
                zoneRepository,
                siteRepository,
                serviceRepository,
                reviewRepository
        );
    }

    @Bean
    public CatalogAccess catalogAccess() {
        return new CatalogAccess();
    }

    @Bean
    public TransactionalOutboxWriter transactionalOutboxWriter(
            TransactionPort transaction,
            OutboxPort outbox
    ) {
        return new TransactionalOutboxWriter(transaction, outbox);
    }

    @Bean
    public CreateCategoryUseCase createCategoryUseCase(
            CategoryRepositoryPort categoryRepository,
            SiteRepositoryPort siteRepository,
            ServiceRepositoryPort serviceRepository,
            CatalogLookup lookup,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new CategoryCreateService(
                categoryRepository,
                siteRepository,
                serviceRepository,
                lookup,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public UpdateCategoryUseCase updateCategoryUseCase(
            CategoryRepositoryPort categoryRepository,
            SiteRepositoryPort siteRepository,
            ServiceRepositoryPort serviceRepository,
            CatalogLookup lookup,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new CategoryUpdateService(
                categoryRepository,
                siteRepository,
                serviceRepository,
                lookup,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public GetCategoryUseCase getCategoryUseCase(CatalogLookup lookup) {
        return new CategoryGetService(lookup);
    }

    @Bean
    public ListCategoriesUseCase listCategoriesUseCase(CategoryRepositoryPort categoryRepository) {
        return new CategoryListService(categoryRepository);
    }

    @Bean
    public CreateZoneUseCase createZoneUseCase(
            ZoneRepositoryPort zoneRepository,
            CatalogAccess access,
            Clock clock
    ) {
        return new ZoneCreateService(zoneRepository, access, clock);
    }

    @Bean
    public UpdateZoneUseCase updateZoneUseCase(
            ZoneRepositoryPort zoneRepository,
            SiteRepositoryPort siteRepository,
            ServiceRepositoryPort serviceRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            Clock clock
    ) {
        return new ZoneUpdateService(
                zoneRepository,
                siteRepository,
                serviceRepository,
                lookup,
                access,
                clock
        );
    }

    @Bean
    public ChangeZoneStatusUseCase changeZoneStatusUseCase(
            ZoneRepositoryPort zoneRepository,
            SiteRepositoryPort siteRepository,
            ServiceRepositoryPort serviceRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            Clock clock
    ) {
        return new ZoneChangeStatusService(
                zoneRepository,
                siteRepository,
                serviceRepository,
                lookup,
                access,
                clock
        );
    }

    @Bean
    public GetZoneUseCase getZoneUseCase(CatalogLookup lookup) {
        return new ZoneGetService(lookup);
    }

    @Bean
    public ListZonesUseCase listZonesUseCase(ZoneRepositoryPort zoneRepository) {
        return new ZoneListService(zoneRepository);
    }

    @Bean
    public CreateSiteUseCase createSiteUseCase(
            SiteRepositoryPort siteRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new SiteCreateService(
                siteRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public UpdateSiteUseCase updateSiteUseCase(
            SiteRepositoryPort siteRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new SiteUpdateService(
                siteRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public ChangeSiteStatusUseCase changeSiteStatusUseCase(
            SiteRepositoryPort siteRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new SiteChangeStatusService(
                siteRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public AssignSiteZoneUseCase assignSiteZoneUseCase(
            SiteRepositoryPort siteRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new SiteAssignZoneService(
                siteRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public GetSiteUseCase getSiteUseCase(CatalogLookup lookup) {
        return new SiteGetService(lookup);
    }

    @Bean
    public ListSitesUseCase listSitesUseCase(SiteRepositoryPort siteRepository) {
        return new SiteListService(siteRepository);
    }

    @Bean
    public CreateServiceUseCase createServiceUseCase(
            ServiceRepositoryPort serviceRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ServiceCreateService(
                serviceRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public UpdateServiceUseCase updateServiceUseCase(
            ServiceRepositoryPort serviceRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ServiceUpdateService(
                serviceRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public ChangeServiceStatusUseCase changeServiceStatusUseCase(
            ServiceRepositoryPort serviceRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ServiceChangeStatusService(
                serviceRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public GetServiceUseCase getServiceUseCase(CatalogLookup lookup) {
        return new ServiceGetService(lookup);
    }

    @Bean
    public ListServicesUseCase listServicesUseCase(ServiceRepositoryPort serviceRepository) {
        return new ServiceListService(serviceRepository);
    }

    @Bean
    public CreateReviewUseCase createReviewUseCase(
            ReviewRepositoryPort reviewRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ReviewCreateService(
                reviewRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public RespondToReviewUseCase respondToReviewUseCase(
            ReviewRepositoryPort reviewRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            Clock clock
    ) {
        return new ReviewRespondService(reviewRepository, lookup, access, clock);
    }

    @Bean
    public ChangeReviewStatusUseCase changeReviewStatusUseCase(
            ReviewRepositoryPort reviewRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ReviewChangeStatusService(
                reviewRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public GetReviewUseCase getReviewUseCase(CatalogLookup lookup) {
        return new ReviewGetService(lookup);
    }

    @Bean
    public ListReviewsUseCase listReviewsUseCase(ReviewRepositoryPort reviewRepository) {
        return new ReviewListService(reviewRepository);
    }

    @Bean
    public GetReviewSummaryUseCase getReviewSummaryUseCase(ReviewRepositoryPort reviewRepository) {
        return new ReviewSummaryService(reviewRepository);
    }
}
