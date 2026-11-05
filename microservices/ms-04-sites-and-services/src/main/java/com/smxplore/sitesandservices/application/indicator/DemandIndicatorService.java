package com.smxplore.sitesandservices.application.indicator;

import com.smxplore.sitesandservices.application.support.DemandAccess;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.indicator.DemandBucket;
import com.smxplore.sitesandservices.domain.model.indicator.DemandComparison;
import com.smxplore.sitesandservices.domain.model.indicator.DemandDistribution;
import com.smxplore.sitesandservices.domain.model.indicator.DemandFilter;
import com.smxplore.sitesandservices.domain.model.indicator.DemandGranularity;
import com.smxplore.sitesandservices.domain.model.indicator.DemandIndicators;
import com.smxplore.sitesandservices.domain.model.indicator.DemandQuery;
import com.smxplore.sitesandservices.domain.model.indicator.DemandRecord;
import com.smxplore.sitesandservices.domain.model.indicator.DemandSummary;
import com.smxplore.sitesandservices.domain.model.indicator.ServiceDemand;
import com.smxplore.sitesandservices.domain.model.indicator.SiteDemand;
import com.smxplore.sitesandservices.domain.model.indicator.ZoneDemand;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.ports.in.GetDemandIndicatorsUseCase;
import com.smxplore.sitesandservices.domain.ports.out.DemandQueryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ZoneRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
public class DemandIndicatorService implements GetDemandIndicatorsUseCase {

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");

    private final DemandQueryPort demandQuery;
    private final ServiceRepositoryPort serviceRepository;
    private final SiteRepositoryPort siteRepository;
    private final ZoneRepositoryPort zoneRepository;
    private final DemandAccess access;
    private final ZoneId zoneId;
    private final Clock clock;

    @Override
    public Mono<DemandIndicators> handle(DemandFilter filter, Actor actor) {
        if (filter == null) {
            return Mono.error(new InvalidCatalogException("demand indicator filter is required"));
        }
        return access.requireReader(actor)
                .flatMap(reader -> resolveScope(reader, filter)
                        .flatMap(scope -> load(scope, filter)));
    }

    private Mono<Scope> resolveScope(Actor actor, DemandFilter filter) {
        if (actor.isAdmin()) {
            if (filter.zoneId() == null && filter.siteId() == null) {
                return Mono.just(new Scope(null, null, "GLOBAL"));
            }
            return scopedServices(
                    null,
                    filter,
                    new CandidateScope(listOf(filter.zoneId()), listOf(filter.siteId())),
                    "GLOBAL_FILTERED");
        }
        if (actor.isGestora()) {
            return Mono.zip(
                            zoneRepository.findByManagerId(actor.userId())
                                    .map(Zone::zoneId)
                                    .collectList(),
                            siteRepository.findByManagerId(actor.userId())
                                    .collectList())
                    .flatMap(tuple -> {
                        List<UUID> ownZoneIds = tuple.getT1();
                        List<Site> ownSites = tuple.getT2();
                        if (filter.zoneId() != null
                                && !ownZoneIds.contains(filter.zoneId())) {
                            return Mono.error(new ForbiddenOperationException(
                                    "The destination manager does not own the requested zone"));
                        }
                        if (filter.siteId() != null
                                && ownSites.stream().noneMatch(site -> site.siteId().equals(filter.siteId()))) {
                            return Mono.error(new ForbiddenOperationException(
                                    "The destination manager does not own the requested site"));
                        }
                        List<UUID> zoneIds = filter.zoneId() != null
                                ? List.of(filter.zoneId())
                                : ownZoneIds;
                        List<UUID> siteIds = filter.siteId() != null
                                ? List.of(filter.siteId())
                                : ownSites.stream().map(Site::siteId).toList();
                        if (zoneIds.isEmpty() && siteIds.isEmpty()) {
                            return Mono.just(new Scope(null, List.of(), "MANAGER"));
                        }
                        return scopedServices(
                                null,
                                filter,
                                new CandidateScope(zoneIds, siteIds),
                                "MANAGER");
                    });
        }
        return scopedServices(
                actor.userId(),
                filter,
                new CandidateScope(listOf(filter.zoneId()), listOf(filter.siteId())),
                "PROVIDER");
    }

    private Mono<Scope> scopedServices(
            UUID providerId,
            DemandFilter filter,
            CandidateScope candidates,
            String label
    ) {
        return serviceRepository.findByScope(providerId, candidates.zoneIds(), candidates.siteIds())
                .filter(service -> matchesFilter(service, filter))
                .map(Service::serviceId)
                .distinct()
                .collectList()
                .map(serviceIds -> new Scope(providerId, serviceIds, label));
    }

    private static boolean matchesFilter(Service service, DemandFilter filter) {
        if (filter.zoneId() != null && !filter.zoneId().equals(service.zoneId())) {
            return false;
        }
        return filter.siteId() == null || filter.siteId().equals(service.siteId());
    }

    private Mono<DemandIndicators> load(Scope scope, DemandFilter filter) {
        Mono<List<DemandRecord>> current = records(filter.from(), filter.to(), scope);
        Mono<List<DemandRecord>> previous = records(filter.previousFrom(), filter.from(), scope);

        return Mono.zip(current, previous)
                .flatMap(tuple -> {
                    List<DemandRecord> currentRecords = tuple.getT1();
                    List<DemandRecord> previousRecords = tuple.getT2();
                    Set<UUID> serviceIds = serviceIds(currentRecords, previousRecords);
                    Mono<Map<UUID, Service>> services = serviceIds.isEmpty()
                            ? Mono.just(Map.of())
                            : serviceRepository.findByIds(serviceIds)
                                    .collectMap(Service::serviceId);

                    return services.flatMap(serviceMap -> {
                        Set<UUID> siteIds = serviceMap.values().stream()
                                .map(Service::siteId)
                                .collect(HashSet::new, Set::add, Set::addAll);
                        Mono<Map<UUID, Site>> sites = siteIds.isEmpty()
                                ? Mono.just(Map.of())
                                : siteRepository.findByIds(siteIds)
                                        .collectMap(Site::siteId);
                        return sites.map(siteMap -> build(
                                filter,
                                scope,
                                currentRecords,
                                previousRecords,
                                serviceMap,
                                siteMap));
                    });
                });
    }

    private Mono<List<DemandRecord>> records(Instant from, Instant to, Scope scope) {
        if (scope.serviceIds() != null && scope.serviceIds().isEmpty()) {
            return Mono.just(List.of());
        }
        return demandQuery.find(new DemandQuery(from, to, scope.providerId(), scope.serviceIds()))
                .collectList();
    }

    private static Set<UUID> serviceIds(List<DemandRecord> current, List<DemandRecord> previous) {
        Set<UUID> ids = new HashSet<>();
        current.forEach(record -> ids.add(record.serviceId()));
        previous.forEach(record -> ids.add(record.serviceId()));
        return ids;
    }

    private DemandIndicators build(
            DemandFilter filter,
            Scope scope,
            List<DemandRecord> current,
            List<DemandRecord> previous,
            Map<UUID, Service> services,
            Map<UUID, Site> sites
    ) {
        DemandSummary currentSummary = summary(current);
        DemandSummary previousSummary = summary(previous);
        long days = periodDays(filter);

        return new DemandIndicators(
                filter.from(),
                filter.to(),
                filter.granularity(),
                zoneId.getId(),
                scope.label(),
                currentSummary,
                comparison(currentSummary, previousSummary),
                timeline(current, filter),
                weekdays(current),
                hours(current),
                zones(current, services, sites, filter, currentSummary.people()),
                sites(current, services, sites, filter, currentSummary.people()),
                services(current, services, filter, currentSummary.people()),
                Instant.now(clock)
        );
    }

    private static DemandSummary summary(List<DemandRecord> records) {
        Set<UUID> activeReservations = new HashSet<>();
        Set<UUID> cancelledReservations = new HashSet<>();
        Set<UUID> completedReservations = new HashSet<>();
        Set<UUID> tourists = new HashSet<>();
        long people = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (DemandRecord record : records) {
            if (record.isCancelled()) {
                cancelledReservations.add(record.reservationId());
                continue;
            }
            activeReservations.add(record.reservationId());
            if (record.isCompleted()) {
                completedReservations.add(record.reservationId());
            }
            tourists.add(record.touristId());
            people += record.people();
            revenue = revenue.add(record.itemTotal() == null ? BigDecimal.ZERO : record.itemTotal());
        }
        long reservations = activeReservations.size();
        long cancelled = cancelledReservations.size();
        long denominator = reservations + cancelled;
        double cancellationRate = denominator == 0 ? 0.0 : (double) cancelled / denominator;
        double averagePeople = reservations == 0 ? 0.0 : (double) people / reservations;
        return new DemandSummary(
                reservations,
                people,
                revenue.setScale(2, RoundingMode.HALF_UP),
                cancelled,
                completedReservations.size(),
                tourists.size(),
                cancellationRate,
                averagePeople);
    }

    private static DemandComparison comparison(DemandSummary current, DemandSummary previous) {
        return new DemandComparison(
                current,
                previous,
                percentageChange(current.reservations(), previous.reservations()),
                percentageChange(current.people(), previous.people()),
                percentageChange(current.revenue(), previous.revenue()),
                (current.cancellationRate() - previous.cancellationRate()) * 100.0);
    }

    private static double percentageChange(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? 0.0 : 100.0;
        }
        return ((double) current - previous) / previous * 100.0;
    }

    private static double percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return current == null || current.signum() == 0 ? 0.0 : 100.0;
        }
        return current.subtract(previous)
                .divide(previous, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private List<DemandBucket> timeline(List<DemandRecord> records, DemandFilter filter) {
        Map<String, GroupTotals> buckets = new LinkedHashMap<>();
        LocalDate lastDay = lastIncludedDay(filter);
        if (filter.granularity() == DemandGranularity.MONTH) {
            YearMonth start = YearMonth.from(filter.from().atZone(zoneId));
            YearMonth end = YearMonth.from(lastDay);
            for (YearMonth cursor = start; !cursor.isAfter(end); cursor = cursor.plusMonths(1)) {
                buckets.put(cursor.toString(), new GroupTotals());
            }
        } else {
            LocalDate start = filter.from().atZone(zoneId).toLocalDate();
            for (LocalDate cursor = start; !cursor.isAfter(lastDay); cursor = cursor.plusDays(1)) {
                buckets.put(cursor.toString(), new GroupTotals());
            }
        }
        for (DemandRecord record : records) {
            if (record.isCancelled()) {
                continue;
            }
            String key = filter.granularity() == DemandGranularity.MONTH
                    ? YearMonth.from(record.bookedFor().atZone(zoneId)).toString()
                    : record.bookedFor().atZone(zoneId).toLocalDate().toString();
            buckets.computeIfAbsent(key, ignored -> new GroupTotals()).add(record);
        }
        return buckets.entrySet().stream()
                .map(entry -> new DemandBucket(
                        entry.getKey(),
                        entry.getValue().reservations.size(),
                        entry.getValue().people,
                        entry.getValue().revenue.setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    private List<DemandDistribution> weekdays(List<DemandRecord> records) {
        Map<DayOfWeek, GroupTotals> groups = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            groups.put(day, new GroupTotals());
        }
        for (DemandRecord record : records) {
            if (!record.isCancelled()) {
                groups.get(record.bookedFor().atZone(zoneId).getDayOfWeek()).add(record);
            }
        }
        return groups.entrySet().stream()
                .map(entry -> new DemandDistribution(
                        entry.getKey().name(),
                        entry.getKey().getDisplayName(TextStyle.FULL, SPANISH),
                        entry.getValue().reservations.size(),
                        entry.getValue().people))
                .toList();
    }

    private List<DemandDistribution> hours(List<DemandRecord> records) {
        GroupTotals[] groups = new GroupTotals[24];
        for (int hour = 0; hour < groups.length; hour++) {
            groups[hour] = new GroupTotals();
        }
        for (DemandRecord record : records) {
            if (!record.isCancelled()) {
                groups[record.bookedFor().atZone(zoneId).getHour()].add(record);
            }
        }
        List<DemandDistribution> distribution = new ArrayList<>(groups.length);
        for (int hour = 0; hour < groups.length; hour++) {
            distribution.add(new DemandDistribution(
                    "%02d".formatted(hour),
                    "%02d:00".formatted(hour),
                    groups[hour].reservations.size(),
                    groups[hour].people));
        }
        return distribution;
    }

    private List<ZoneDemand> zones(
            List<DemandRecord> records,
            Map<UUID, Service> services,
            Map<UUID, Site> sites,
            DemandFilter filter,
            long totalPeople
    ) {
        Map<UUID, Group> groups = new LinkedHashMap<>();
        for (DemandRecord record : records) {
            if (record.isCancelled()) {
                continue;
            }
            Service service = services.get(record.serviceId());
            if (service == null) {
                continue;
            }
            Group group = groups.computeIfAbsent(service.zoneId(), ignored -> new Group());
            group.name = service.zoneName();
            group.type = service.zoneStatus() == null ? null : service.zoneStatus().name();
            group.add(record);
        }
        long days = periodDays(filter);
        return groups.entrySet().stream()
                .map(entry -> {
                    long capacity = sites.values().stream()
                            .filter(site -> entry.getKey().equals(site.zoneId()))
                            .distinct()
                            .mapToLong(site -> siteCapacity(site, days))
                            .sum();
                    Group group = entry.getValue();
                    return new ZoneDemand(
                            entry.getKey(),
                            group.name,
                            group.type,
                            group.reservations.size(),
                            group.people,
                            group.revenue.setScale(2, RoundingMode.HALF_UP),
                            share(group.people, totalPeople),
                            capacity,
                            occupancy(group.people, capacity));
                })
                .sorted(Comparator.comparingLong(ZoneDemand::people).reversed()
                        .thenComparing(ZoneDemand::zoneName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<SiteDemand> sites(
            List<DemandRecord> records,
            Map<UUID, Service> services,
            Map<UUID, Site> sites,
            DemandFilter filter,
            long totalPeople
    ) {
        Map<UUID, Group> groups = new LinkedHashMap<>();
        for (DemandRecord record : records) {
            if (record.isCancelled()) {
                continue;
            }
            Service service = services.get(record.serviceId());
            if (service == null) {
                continue;
            }
            Site site = sites.get(service.siteId());
            if (site == null) {
                continue;
            }
            Group group = groups.computeIfAbsent(site.siteId(), ignored -> new Group());
            group.name = site.name();
            group.zoneId = site.zoneId();
            group.zoneName = site.zoneName();
            group.add(record);
        }
        long days = periodDays(filter);
        return groups.entrySet().stream()
                .map(entry -> {
                    Site site = sites.get(entry.getKey());
                    Group group = entry.getValue();
                    long capacity = site == null ? 0 : siteCapacity(site, days);
                    return new SiteDemand(
                            entry.getKey(),
                            group.name,
                            group.zoneId,
                            group.zoneName,
                            group.reservations.size(),
                            group.people,
                            group.revenue.setScale(2, RoundingMode.HALF_UP),
                            share(group.people, totalPeople),
                            capacity,
                            occupancy(group.people, capacity));
                })
                .sorted(Comparator.comparingLong(SiteDemand::people).reversed()
                        .thenComparing(SiteDemand::siteName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<ServiceDemand> services(
            List<DemandRecord> records,
            Map<UUID, Service> services,
            DemandFilter filter,
            long totalPeople
    ) {
        Map<UUID, Group> groups = new LinkedHashMap<>();
        for (DemandRecord record : records) {
            if (record.isCancelled()) {
                continue;
            }
            Service service = services.get(record.serviceId());
            Group group = groups.computeIfAbsent(record.serviceId(), ignored -> new Group());
            group.name = service == null ? record.serviceName() : service.name();
            group.type = service == null ? record.serviceType() : service.type();
            if (service != null) {
                group.siteId = service.siteId();
                group.siteName = service.siteName();
                group.zoneId = service.zoneId();
                group.zoneName = service.zoneName();
            }
            group.add(record);
        }
        long days = periodDays(filter);
        return groups.entrySet().stream()
                .map(entry -> {
                    Service service = services.get(entry.getKey());
                    Group group = entry.getValue();
                    long capacity = service == null ? 0 : serviceCapacity(service, filter, days);
                    return new ServiceDemand(
                            entry.getKey(),
                            group.name,
                            group.type,
                            group.siteId,
                            group.siteName,
                            group.zoneId,
                            group.zoneName,
                            group.reservations.size(),
                            group.people,
                            group.revenue.setScale(2, RoundingMode.HALF_UP),
                            share(group.people, totalPeople),
                            capacity,
                            occupancy(group.people, capacity));
                })
                .sorted(Comparator.comparingLong(ServiceDemand::people).reversed()
                        .thenComparing(ServiceDemand::serviceName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private long serviceCapacity(Service service, DemandFilter filter, long days) {
        if (service == null || days <= 0) {
            return 0;
        }
        LocalDate start = filter.from().atZone(zoneId).toLocalDate();
        LocalDate end = lastIncludedDay(filter);
        long capacity = 0;
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusDays(1)) {
            capacity += dailyCapacity(service, cursor.getDayOfWeek());
        }
        return capacity;
    }

    private static int dailyCapacity(Service service, DayOfWeek dayOfWeek) {
        if (service.availability() == null || service.availability().isEmpty()) {
            return service.maxCapacity();
        }
        int slotCapacity = service.availability().stream()
                .filter(slot -> slot.dayOfWeek() == dayOfWeek)
                .map(AvailabilitySlot::capacity)
                .filter(capacity -> capacity != null && capacity > 0)
                .mapToInt(Integer::intValue)
                .sum();
        if (slotCapacity == 0) {
            return service.maxCapacity();
        }
        return Math.min(service.maxCapacity(), slotCapacity);
    }

    private static long siteCapacity(Site site, long days) {
        if (site == null) {
            return 0;
        }
        return (long) site.maxCapacity() * Math.max(days, 1);
    }

    private long periodDays(DemandFilter filter) {
        LocalDate start = filter.from().atZone(zoneId).toLocalDate();
        LocalDate end = lastIncludedDay(filter);
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    private LocalDate lastIncludedDay(DemandFilter filter) {
        var end = filter.to().atZone(zoneId);
        LocalDate endDate = end.toLocalDate();
        if (end.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            endDate = endDate.minusDays(1);
        }
        LocalDate start = filter.from().atZone(zoneId).toLocalDate();
        return endDate.isBefore(start) ? start : endDate;
    }

    private static double share(long people, long totalPeople) {
        return totalPeople == 0 ? 0.0 : (double) people / totalPeople;
    }

    private static double occupancy(long people, long capacity) {
        if (capacity <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) people / capacity);
    }

    private static <T> List<T> listOf(T value) {
        return value == null ? List.of() : List.of(value);
    }

    private record Scope(UUID providerId, List<UUID> serviceIds, String label) {
    }

    private record CandidateScope(Collection<UUID> zoneIds, Collection<UUID> siteIds) {
    }

    private static final class GroupTotals {
        private final Set<UUID> reservations = new HashSet<>();
        private long people;
        private BigDecimal revenue = BigDecimal.ZERO;

        void add(DemandRecord record) {
            reservations.add(record.reservationId());
            people += record.people();
            revenue = revenue.add(record.itemTotal() == null ? BigDecimal.ZERO : record.itemTotal());
        }
    }

    private static final class Group {
        private String name;
        private String type;
        private UUID siteId;
        private String siteName;
        private UUID zoneId;
        private String zoneName;
        private final Set<UUID> reservations = new HashSet<>();
        private long people;
        private BigDecimal revenue = BigDecimal.ZERO;

        void add(DemandRecord record) {
            reservations.add(record.reservationId());
            people += record.people();
            revenue = revenue.add(record.itemTotal() == null ? BigDecimal.ZERO : record.itemTotal());
        }
    }
}
