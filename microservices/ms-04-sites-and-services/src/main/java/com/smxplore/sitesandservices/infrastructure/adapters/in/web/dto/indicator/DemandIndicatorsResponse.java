package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.indicator;

import com.smxplore.sitesandservices.domain.model.indicator.DemandBucket;
import com.smxplore.sitesandservices.domain.model.indicator.DemandComparison;
import com.smxplore.sitesandservices.domain.model.indicator.DemandDistribution;
import com.smxplore.sitesandservices.domain.model.indicator.DemandGranularity;
import com.smxplore.sitesandservices.domain.model.indicator.DemandIndicators;
import com.smxplore.sitesandservices.domain.model.indicator.DemandSummary;
import com.smxplore.sitesandservices.domain.model.indicator.ServiceDemand;
import com.smxplore.sitesandservices.domain.model.indicator.SiteDemand;
import com.smxplore.sitesandservices.domain.model.indicator.ZoneDemand;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DemandIndicatorsResponse(
        Instant from,
        Instant to,
        DemandGranularity granularity,
        String timeZone,
        String scope,
        DemandSummaryResponse summary,
        DemandComparisonResponse comparison,
        List<DemandBucketResponse> timeline,
        List<DemandDistributionResponse> weekdays,
        List<DemandDistributionResponse> hours,
        List<ZoneDemandResponse> zones,
        List<SiteDemandResponse> sites,
        List<ServiceDemandResponse> services,
        Instant generatedAt
) {

    public static DemandIndicatorsResponse from(DemandIndicators indicators) {
        return new DemandIndicatorsResponse(
                indicators.from(),
                indicators.to(),
                indicators.granularity(),
                indicators.timeZone(),
                indicators.scope(),
                DemandSummaryResponse.from(indicators.summary()),
                DemandComparisonResponse.from(indicators.comparison()),
                indicators.timeline().stream().map(DemandBucketResponse::from).toList(),
                indicators.weekdays().stream().map(DemandDistributionResponse::from).toList(),
                indicators.hours().stream().map(DemandDistributionResponse::from).toList(),
                indicators.zones().stream().map(ZoneDemandResponse::from).toList(),
                indicators.sites().stream().map(SiteDemandResponse::from).toList(),
                indicators.services().stream().map(ServiceDemandResponse::from).toList(),
                indicators.generatedAt()
        );
    }

    public record DemandSummaryResponse(
            long reservations,
            long people,
            BigDecimal revenue,
            long cancelledReservations,
            long completedReservations,
            long uniqueTourists,
            double cancellationRate,
            double averagePeoplePerReservation
    ) {

        public static DemandSummaryResponse from(DemandSummary summary) {
            return new DemandSummaryResponse(
                    summary.reservations(),
                    summary.people(),
                    summary.revenue(),
                    summary.cancelledReservations(),
                    summary.completedReservations(),
                    summary.uniqueTourists(),
                    summary.cancellationRate(),
                    summary.averagePeoplePerReservation()
            );
        }
    }

    public record DemandComparisonResponse(
            DemandSummaryResponse current,
            DemandSummaryResponse previous,
            double reservationsChange,
            double peopleChange,
            double revenueChange,
            double cancellationRateChange
    ) {

        public static DemandComparisonResponse from(DemandComparison comparison) {
            return new DemandComparisonResponse(
                    DemandSummaryResponse.from(comparison.current()),
                    DemandSummaryResponse.from(comparison.previous()),
                    comparison.reservationsChange(),
                    comparison.peopleChange(),
                    comparison.revenueChange(),
                    comparison.cancellationRateChange()
            );
        }
    }

    public record DemandBucketResponse(
            String period,
            long reservations,
            long people,
            BigDecimal revenue
    ) {

        public static DemandBucketResponse from(DemandBucket bucket) {
            return new DemandBucketResponse(
                    bucket.period(),
                    bucket.reservations(),
                    bucket.people(),
                    bucket.revenue()
            );
        }
    }

    public record DemandDistributionResponse(
            String key,
            String label,
            long reservations,
            long people
    ) {

        public static DemandDistributionResponse from(DemandDistribution distribution) {
            return new DemandDistributionResponse(
                    distribution.key(),
                    distribution.label(),
                    distribution.reservations(),
                    distribution.people()
            );
        }
    }

    public record ZoneDemandResponse(
            UUID zoneId,
            String zoneName,
            String zoneStatus,
            long reservations,
            long people,
            BigDecimal revenue,
            double share,
            long capacityPeople,
            double occupancyRate
    ) {

        public static ZoneDemandResponse from(ZoneDemand demand) {
            return new ZoneDemandResponse(
                    demand.zoneId(),
                    demand.zoneName(),
                    demand.zoneStatus(),
                    demand.reservations(),
                    demand.people(),
                    demand.revenue(),
                    demand.share(),
                    demand.capacityPeople(),
                    demand.occupancyRate()
            );
        }
    }

    public record SiteDemandResponse(
            UUID siteId,
            String siteName,
            UUID zoneId,
            String zoneName,
            long reservations,
            long people,
            BigDecimal revenue,
            double share,
            long capacityPeople,
            double occupancyRate
    ) {

        public static SiteDemandResponse from(SiteDemand demand) {
            return new SiteDemandResponse(
                    demand.siteId(),
                    demand.siteName(),
                    demand.zoneId(),
                    demand.zoneName(),
                    demand.reservations(),
                    demand.people(),
                    demand.revenue(),
                    demand.share(),
                    demand.capacityPeople(),
                    demand.occupancyRate()
            );
        }
    }

    public record ServiceDemandResponse(
            UUID serviceId,
            String serviceName,
            String serviceType,
            UUID siteId,
            String siteName,
            UUID zoneId,
            String zoneName,
            long reservations,
            long people,
            BigDecimal revenue,
            double share,
            long capacityPeople,
            double occupancyRate
    ) {

        public static ServiceDemandResponse from(ServiceDemand demand) {
            return new ServiceDemandResponse(
                    demand.serviceId(),
                    demand.serviceName(),
                    demand.serviceType(),
                    demand.siteId(),
                    demand.siteName(),
                    demand.zoneId(),
                    demand.zoneName(),
                    demand.reservations(),
                    demand.people(),
                    demand.revenue(),
                    demand.share(),
                    demand.capacityPeople(),
                    demand.occupancyRate()
            );
        }
    }
}
