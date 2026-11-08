package com.smxplore.sitesandservices.infrastructure.config;

import com.smxplore.sitesandservices.application.catalog.CatalogExportService;
import com.smxplore.sitesandservices.application.catalog.CategoryCreateService;
import com.smxplore.sitesandservices.application.catalog.CategoryGetService;
import com.smxplore.sitesandservices.application.catalog.CategoryListService;
import com.smxplore.sitesandservices.application.catalog.CategoryUpdateService;
import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.indicator.DemandIndicatorService;
import com.smxplore.sitesandservices.application.notification.LowCapacityAlertService;
import com.smxplore.sitesandservices.application.notification.LowCapacityPolicy;
import com.smxplore.sitesandservices.application.notification.NotificationAccess;
import com.smxplore.sitesandservices.application.notification.NotificationContactService;
import com.smxplore.sitesandservices.application.notification.NotificationCreateService;
import com.smxplore.sitesandservices.application.notification.NotificationListService;
import com.smxplore.sitesandservices.application.notification.NotificationMarkReadService;
import com.smxplore.sitesandservices.application.review.ReviewAiFlaggedListService;
import com.smxplore.sitesandservices.application.review.ReviewChangeStatusService;
import com.smxplore.sitesandservices.application.review.ReviewCreateService;
import com.smxplore.sitesandservices.application.review.ReviewGetService;
import com.smxplore.sitesandservices.application.review.ReviewListService;
import com.smxplore.sitesandservices.application.review.ReviewReportCreateService;
import com.smxplore.sitesandservices.application.review.ReviewReportListService;
import com.smxplore.sitesandservices.application.review.ReviewReportResolveService;
import com.smxplore.sitesandservices.application.review.ReviewReputationService;
import com.smxplore.sitesandservices.application.review.ReviewRespondService;
import com.smxplore.sitesandservices.application.reservation.ReservationAvailabilityService;
import com.smxplore.sitesandservices.application.reservation.ReservationChangeStatusService;
import com.smxplore.sitesandservices.application.reservation.ReservationCompletionService;
import com.smxplore.sitesandservices.application.reservation.ReservationCreateService;
import com.smxplore.sitesandservices.application.reservation.ReservationGetService;
import com.smxplore.sitesandservices.application.reservation.ReservationListService;
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
import com.smxplore.sitesandservices.application.support.DemandAccess;
import com.smxplore.sitesandservices.application.support.ReservationAccess;
import com.smxplore.sitesandservices.application.support.ReservationAvailabilityPolicy;
import com.smxplore.sitesandservices.application.support.ReservationPolicy;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.application.support.TransactionalPgOutboxWriter;
import com.smxplore.sitesandservices.application.zone.ZoneChangeStatusService;
import com.smxplore.sitesandservices.application.zone.ZoneCreateService;
import com.smxplore.sitesandservices.application.zone.ZoneGetService;
import com.smxplore.sitesandservices.application.zone.ZoneListService;
import com.smxplore.sitesandservices.application.zone.ZoneUpdateService;
import com.smxplore.sitesandservices.domain.model.review.ReviewAiPolicy;
import com.smxplore.sitesandservices.domain.ports.in.AssignSiteZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ChangeReservationStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ChangeReviewStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ChangeServiceStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ChangeSiteStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ChangeZoneStatusUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CheckReservationAvailabilityUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateCategoryUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateReservationUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateServiceUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateSiteUseCase;
import com.smxplore.sitesandservices.domain.ports.in.CreateZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ExportCatalogUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetCategoryUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetDemandIndicatorsUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetReputationUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetReservationUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetReviewSummaryUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetServiceUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetSiteUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListAiFlaggedReviewsUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListCategoriesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListReviewReportsUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListNotificationsUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListReservationsUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListReviewsUseCase;
import com.smxplore.sitesandservices.domain.ports.in.MarkNotificationReadUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListServicesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListSitesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListZonesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ReportReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ResolveReviewReportUseCase;
import com.smxplore.sitesandservices.domain.ports.in.RespondToReviewUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateCategoryUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateServiceUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateSiteUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateZoneUseCase;
import com.smxplore.sitesandservices.domain.ports.out.CategoryRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.DemandQueryPort;
import com.smxplore.sitesandservices.domain.ports.out.NotificationContactRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.NotificationDeliveryPort;
import com.smxplore.sitesandservices.domain.ports.out.NotificationRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.OutboxPort;
import com.smxplore.sitesandservices.domain.ports.out.ReservationOutboxPort;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ReservationTransactionPort;
import com.smxplore.sitesandservices.domain.ports.out.ReviewAiFilter;
import com.smxplore.sitesandservices.domain.ports.out.ReviewReportRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.TransactionPort;
import com.smxplore.sitesandservices.domain.ports.out.ZoneRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

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
            ReviewRepositoryPort reviewRepository,
            ReviewReportRepositoryPort reviewReportRepository
    ) {
        return new CatalogLookup(
                categoryRepository,
                zoneRepository,
                siteRepository,
                serviceRepository,
                reviewRepository,
                reviewReportRepository
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

    /**
     * Outbox transaccional para los agregados de PostgreSQL (zonas). Comparte la tabla
     * {@code reservation_outbox} y el relay existentes: la tabla es el outbox PG del servicio.
     */
    @Bean
    public TransactionalPgOutboxWriter transactionalPgOutboxWriter(
            ReservationTransactionPort transaction,
            ReservationOutboxPort outbox
    ) {
        return new TransactionalPgOutboxWriter(transaction, outbox);
    }

    @Bean
    public CreateCategoryUseCase createCategoryUseCase(
            CategoryRepositoryPort categoryRepository,
            SiteRepositoryPort siteRepository,
            ServiceRepositoryPort serviceRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new CategoryCreateService(
                categoryRepository,
                siteRepository,
                serviceRepository,
                lookup,
                access,
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
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new CategoryUpdateService(
                categoryRepository,
                siteRepository,
                serviceRepository,
                lookup,
                access,
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
            TransactionalPgOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ZoneCreateService(zoneRepository, access, outboxWriter, eventFactory, clock);
    }

    @Bean
    public UpdateZoneUseCase updateZoneUseCase(
            ZoneRepositoryPort zoneRepository,
            SiteRepositoryPort siteRepository,
            ServiceRepositoryPort serviceRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalPgOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ZoneUpdateService(
                zoneRepository,
                siteRepository,
                serviceRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
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
            TransactionalPgOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ZoneChangeStatusService(
                zoneRepository,
                siteRepository,
                serviceRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
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
            ServiceRepositoryPort serviceRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new SiteUpdateService(
                siteRepository,
                serviceRepository,
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
    public GetSiteUseCase getSiteUseCase(CatalogLookup lookup, CatalogAccess access) {
        return new SiteGetService(lookup, access);
    }

    @Bean
    public ListSitesUseCase listSitesUseCase(SiteRepositoryPort siteRepository, CatalogAccess access) {
        return new SiteListService(siteRepository, access);
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
    public GetServiceUseCase getServiceUseCase(CatalogLookup lookup, CatalogAccess access) {
        return new ServiceGetService(lookup, access);
    }

    @Bean
    public ListServicesUseCase listServicesUseCase(
            ServiceRepositoryPort serviceRepository,
            CatalogAccess access
    ) {
        return new ServiceListService(serviceRepository, access);
    }

    @Bean
    public ReviewAiPolicy reviewAiPolicy(ReviewAiProperties properties) {
        return new ReviewAiPolicy(properties.threshold(), properties.action(), properties.failOpen());
    }

    @Bean
    public CreateReviewUseCase createReviewUseCase(
            ReviewRepositoryPort reviewRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            ReviewAiFilter aiFilter,
            ReviewAiPolicy aiPolicy,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ReviewCreateService(
                reviewRepository,
                lookup,
                access,
                aiFilter,
                aiPolicy,
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
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ReviewRespondService(
                reviewRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
                clock
        );
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
    public ListAiFlaggedReviewsUseCase listAiFlaggedReviewsUseCase(ReviewRepositoryPort reviewRepository) {
        return new ReviewAiFlaggedListService(reviewRepository);
    }

    @Bean
    public GetReviewSummaryUseCase getReviewSummaryUseCase(ReviewRepositoryPort reviewRepository) {
        return new ReviewSummaryService(reviewRepository);
    }

    @Bean
    public GetReputationUseCase getReputationUseCase(ReviewRepositoryPort reviewRepository) {
        return new ReviewReputationService(reviewRepository);
    }

    @Bean
    public ReportReviewUseCase reportReviewUseCase(
            ReviewReportRepositoryPort reviewReportRepository,
            CatalogLookup lookup,
            Clock clock
    ) {
        return new ReviewReportCreateService(reviewReportRepository, lookup, clock);
    }

    @Bean
    public ListReviewReportsUseCase listReviewReportsUseCase(
            ReviewReportRepositoryPort reviewReportRepository,
            CatalogAccess access
    ) {
        return new ReviewReportListService(reviewReportRepository, access);
    }

    @Bean
    public ResolveReviewReportUseCase resolveReviewReportUseCase(
            ReviewReportRepositoryPort reviewReportRepository,
            ReviewRepositoryPort reviewRepository,
            CatalogLookup lookup,
            CatalogAccess access,
            TransactionalOutboxWriter outboxWriter,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ReviewReportResolveService(
                reviewReportRepository,
                reviewRepository,
                lookup,
                access,
                outboxWriter,
                eventFactory,
                clock
        );
    }

    @Bean
    public ExportCatalogUseCase exportCatalogUseCase(
            CategoryRepositoryPort categoryRepository,
            SiteRepositoryPort siteRepository,
            ServiceRepositoryPort serviceRepository,
            DomainEventFactory eventFactory,
            CatalogAccess access
    ) {
        return new CatalogExportService(
                categoryRepository,
                siteRepository,
                serviceRepository,
                eventFactory,
                access
        );
    }

    @Bean
    public ReservationAccess reservationAccess() {
        return new ReservationAccess();
    }

    @Bean
    public DemandAccess demandAccess() {
        return new DemandAccess();
    }

    @Bean
    public GetDemandIndicatorsUseCase getDemandIndicatorsUseCase(
            DemandQueryPort demandQuery,
            ServiceRepositoryPort serviceRepository,
            SiteRepositoryPort siteRepository,
            ZoneRepositoryPort zoneRepository,
            DemandAccess access,
            BookingProperties properties,
            Clock clock
    ) {
        return new DemandIndicatorService(
                demandQuery,
                serviceRepository,
                siteRepository,
                zoneRepository,
                access,
                properties.zone(),
                clock
        );
    }

    @Bean
    public NotificationAccess notificationAccess() {
        return new NotificationAccess();
    }

    @Bean
    public NotificationCreateService notificationCreateService(
            NotificationRepositoryPort notificationRepository,
            List<NotificationDeliveryPort> notificationDeliveries,
            Clock clock
    ) {
        return new NotificationCreateService(
                notificationRepository, notificationDeliveries, clock);
    }

    @Bean
    public LowCapacityAlertService lowCapacityAlertService(
            NotificationRepositoryPort notificationRepository,
            List<NotificationDeliveryPort> notificationDeliveries,
            NotificationLowCapacityProperties properties,
            Clock clock
    ) {
        return new LowCapacityAlertService(
                notificationRepository,
                notificationDeliveries,
                new LowCapacityPolicy(properties.isEnabled(), properties.getThresholdPercent()),
                clock);
    }

    @Bean
    public NotificationContactService notificationContactService(
            NotificationContactRepositoryPort contactRepository,
            CatalogAccess access,
            Clock clock
    ) {
        return new NotificationContactService(contactRepository, access, clock);
    }

    @Bean
    public ListNotificationsUseCase listNotificationsUseCase(
            NotificationRepositoryPort notificationRepository,
            NotificationAccess access
    ) {
        return new NotificationListService(notificationRepository, access);
    }

    @Bean
    public MarkNotificationReadUseCase markNotificationReadUseCase(
            NotificationRepositoryPort notificationRepository,
            NotificationAccess access,
            Clock clock
    ) {
        return new NotificationMarkReadService(notificationRepository, access, clock);
    }

    @Bean
    public ReservationAvailabilityPolicy reservationAvailabilityPolicy(
            BookingProperties properties
    ) {
        return new ReservationAvailabilityPolicy(properties.zone());
    }

    @Bean
    public ReservationPolicy reservationPolicy(BookingProperties properties) {
        return new ReservationPolicy(
                properties.getMaxActivePerTourist(),
                properties.getMaxCreationsPerMinute(),
                properties.getMaxAdvanceDays()
        );
    }

    @Bean
    public CreateReservationUseCase createReservationUseCase(
            ServiceRepositoryPort serviceRepository,
            SiteRepositoryPort siteRepository,
            ReservationRepositoryPort reservationRepository,
            ReservationTransactionPort transaction,
            ReservationOutboxPort outbox,
            ReservationAvailabilityPolicy availabilityPolicy,
            ReservationAccess access,
            DomainEventFactory eventFactory,
            ReservationPolicy policy,
            LowCapacityAlertService lowCapacityAlertService,
            Clock clock
    ) {
        return new ReservationCreateService(
                serviceRepository,
                siteRepository,
                reservationRepository,
                transaction,
                outbox,
                availabilityPolicy,
                access,
                eventFactory,
                policy,
                lowCapacityAlertService,
                clock
        );
    }

    @Bean
    public GetReservationUseCase getReservationUseCase(
            ReservationRepositoryPort reservationRepository,
            ReservationAccess access
    ) {
        return new ReservationGetService(reservationRepository, access);
    }

    @Bean
    public ListReservationsUseCase listReservationsUseCase(
            ReservationRepositoryPort reservationRepository,
            ReservationAccess access
    ) {
        return new ReservationListService(reservationRepository, access);
    }

    @Bean
    public ChangeReservationStatusUseCase changeReservationStatusUseCase(
            ReservationRepositoryPort reservationRepository,
            ReservationTransactionPort transaction,
            ReservationOutboxPort outbox,
            ReservationAccess access,
            DomainEventFactory eventFactory,
            Clock clock
    ) {
        return new ReservationChangeStatusService(
                reservationRepository,
                transaction,
                outbox,
                access,
                eventFactory,
                clock
        );
    }

    @Bean
    public CheckReservationAvailabilityUseCase checkReservationAvailabilityUseCase(
            ServiceRepositoryPort serviceRepository,
            ReservationRepositoryPort reservationRepository,
            ReservationAvailabilityPolicy availabilityPolicy,
            Clock clock
    ) {
        return new ReservationAvailabilityService(
                serviceRepository,
                reservationRepository,
                availabilityPolicy,
                clock
        );
    }

    @Bean
    public ReservationCompletionService reservationCompletionService(
            ReservationRepositoryPort reservationRepository,
            ChangeReservationStatusUseCase changeStatusUseCase,
            BookingProperties properties,
            Clock clock
    ) {
        return new ReservationCompletionService(
                reservationRepository,
                changeStatusUseCase,
                properties.getCompletionBatchSize(),
                clock
        );
    }
}
