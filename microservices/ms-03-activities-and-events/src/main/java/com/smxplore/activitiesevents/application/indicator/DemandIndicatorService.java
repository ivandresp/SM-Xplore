package com.smxplore.activitiesevents.application.indicator;

import com.smxplore.activitiesevents.domain.exceptions.indicator.DemandAccessDeniedException;
import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.model.indicator.ActivityDemand;
import com.smxplore.activitiesevents.domain.model.indicator.DemandBucket;
import com.smxplore.activitiesevents.domain.model.indicator.DemandComparison;
import com.smxplore.activitiesevents.domain.model.indicator.DemandDistribution;
import com.smxplore.activitiesevents.domain.model.indicator.DemandFilter;
import com.smxplore.activitiesevents.domain.model.indicator.DemandGranularity;
import com.smxplore.activitiesevents.domain.model.indicator.DemandIndicators;
import com.smxplore.activitiesevents.domain.model.indicator.DemandQuery;
import com.smxplore.activitiesevents.domain.model.indicator.DemandRecord;
import com.smxplore.activitiesevents.domain.model.indicator.DemandSummary;
import com.smxplore.activitiesevents.domain.model.indicator.EventDemand;
import com.smxplore.activitiesevents.domain.model.indicator.ServiceDemand;
import com.smxplore.activitiesevents.domain.model.indicator.SiteDemand;
import com.smxplore.activitiesevents.domain.model.indicator.ZoneDemand;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.DemandQueryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.Ms04DemandPort;
import com.smxplore.activitiesevents.domain.ports.repository.SiteCatalogPort;
import com.smxplore.activitiesevents.domain.ports.usecases.indicator.GetDemandIndicatorsUseCasePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Demand indicators for activities and events. The guide sees only their own activities; the
 * destination manager sees the activities and events of their sites plus, through ms-04, the
 * services of the same destination; the admin sees the global picture.
 */
@Slf4j
@RequiredArgsConstructor
public class DemandIndicatorService implements GetDemandIndicatorsUseCasePort {

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");

    private final DemandQueryPort demandQuery;
    private final ActivityRepositoryPort activityRepository;
    private final EventRepositoryPort eventRepository;
    private final SiteCatalogPort siteCatalog;
    private final Ms04DemandPort ms04Demand;
    private final ZoneId zoneId;
    private final Clock clock;

    @Override
    public Mono<DemandIndicators> handle(DemandFilter filter, UUID userId, UserRole role, String bearerToken) {
        if (filter == null) {
            return Mono.error(new DemandAccessDeniedException("Demand indicator filter is required."));
        }
        if (role == null || (role != UserRole.GUIDE
                && role != UserRole.DESTINATION_MANAGER
                && role != UserRole.ADMIN)) {
            return Mono.error(new DemandAccessDeniedException(
                    "Only guides, destination managers or administrators can view demand indicators."));
        }
        return resolveScope(filter, userId, role, bearerToken)
                .flatMap(scope -> load(scope, filter, bearerToken));
    }

    private Mono<Scope> resolveScope(DemandFilter filter, UUID userId, UserRole role, String bearerToken) {
        return switch (role) {
            case GUIDE -> guideScope(filter, userId, bearerToken);
            case DESTINATION_MANAGER -> managerScope(filter, userId, bearerToken);
            case ADMIN -> adminScope(filter, bearerToken);
            default -> Mono.error(new DemandAccessDeniedException("Unsupported role."));
        };
    }

    private Mono<Scope> guideScope(DemandFilter filter, UUID userId, String bearerToken) {
        return activityRepository.findAllByGuideId(userId)
                .filter(activity -> activity.getStatus() != ActivityStatus.DRAFT)
                .collectList()
                .flatMap(activities -> ensureSites(
                        activities.stream().map(Activity::getSiteId).toList(), Map.of(), bearerToken)
                        .map(sites -> {
                            var selected = activities.stream()
                                    .filter(activity -> matchesSiteFilter(activity.getSiteId(), filter, sites))
                                    .toList();
                            return new Scope("GUIDE", userId, selected, List.of(), sites, false);
                        }));
    }

    private Mono<Scope> managerScope(DemandFilter filter, UUID userId, String bearerToken) {
        return siteCatalog.findByManagerId(userId, bearerToken)
                .collectList()
                .flatMap(ownSites -> {
                    if (filter.zoneId() != null
                            && ownSites.stream().noneMatch(site -> filter.zoneId().equals(site.zoneId()))) {
                        return Mono.error(new DemandAccessDeniedException(
                                "The destination manager does not own the requested zone."));
                    }
                    if (filter.siteId() != null
                            && ownSites.stream().noneMatch(site -> filter.siteId().equals(site.siteId()))) {
                        return Mono.error(new DemandAccessDeniedException(
                                "The destination manager does not own the requested site."));
                    }
                    var selectedSites = ownSites.stream()
                            .filter(site -> filter.siteId() == null || filter.siteId().equals(site.siteId()))
                            .filter(site -> filter.zoneId() == null || filter.zoneId().equals(site.zoneId()))
                            .toList();
                    if (selectedSites.isEmpty()) {
                        return Mono.just(new Scope("MANAGER", null, List.of(), List.of(), Map.of(), true));
                    }
                    var siteIds = selectedSites.stream().map(SiteCatalogPort.SiteSnapshot::siteId).toList();
                    var siteMap = selectedSites.stream()
                            .collect(java.util.stream.Collectors.toMap(SiteCatalogPort.SiteSnapshot::siteId,
                                    site -> site, (first, second) -> first, LinkedHashMap::new));
                    return loadItemsBySites(siteIds)
                            .map(items -> new Scope("MANAGER", null, items.activities(), items.events(),
                                    siteMap, true));
                });
    }

    private Mono<Scope> adminScope(DemandFilter filter, String bearerToken) {
        Mono<List<SiteCatalogPort.SiteSnapshot>> sites = filter.siteId() != null
                ? siteCatalog.findById(filter.siteId(), bearerToken).flux().collectList()
                : filter.zoneId() != null
                        ? siteCatalog.findByZoneId(filter.zoneId(), bearerToken).collectList()
                        : Mono.just(List.of());
        return sites.flatMap(selectedSites -> {
            var siteMap = selectedSites.stream()
                    .collect(java.util.stream.Collectors.toMap(SiteCatalogPort.SiteSnapshot::siteId,
                            site -> site, (first, second) -> first, LinkedHashMap::new));
            if (filter.siteId() != null || filter.zoneId() != null) {
                if (selectedSites.isEmpty()) {
                    return Mono.just(new Scope("GLOBAL_FILTERED", null, List.of(), List.of(),
                            Map.of(), true));
                }
                return loadItemsBySites(selectedSites.stream()
                                .map(SiteCatalogPort.SiteSnapshot::siteId).toList())
                        .map(items -> new Scope("GLOBAL_FILTERED", null, items.activities(),
                                items.events(), siteMap, true));
            }
            return Mono.zip(
                            activityRepository.findAll().filter(activity -> activity.getStatus() != ActivityStatus.DRAFT)
                                    .collectList(),
                            eventRepository.findAll().filter(event -> event.getStatus() != EventStatus.DRAFT)
                                    .collectList())
                    .map(tuple -> new Scope("GLOBAL", null, tuple.getT1(), tuple.getT2(),
                            siteMap, true));
        });
    }

    private Mono<Items> loadItemsBySites(Collection<UUID> siteIds) {
        return Mono.zip(
                        activityRepository.findIdsBySiteIds(siteIds).collectList()
                                .flatMap(ids -> activityRepository.findByIds(ids).collectList()),
                        eventRepository.findIdsBySiteIds(siteIds).collectList()
                                .flatMap(ids -> eventRepository.findByIds(ids).collectList()))
                .map(tuple -> new Items(
                        tuple.getT1().stream().filter(a -> a.getStatus() != ActivityStatus.DRAFT).toList(),
                        tuple.getT2().stream().filter(e -> e.getStatus() != EventStatus.DRAFT).toList()));
    }

    private Mono<DemandIndicators> load(Scope scope, DemandFilter filter, String bearerToken) {
        var effective = new Scope(
                scope.label(),
                scope.guideId(),
                scope.activities().stream().filter(activity -> inPeriod(activity.getStartsAt(), filter)).toList(),
                scope.events().stream().filter(event -> inPeriod(event.getStartsAt(), filter)).toList(),
                scope.sites(),
                scope.includeServices());
        var itemSiteIds = new HashSet<UUID>();
        effective.activities().forEach(activity -> itemSiteIds.add(activity.getSiteId()));
        effective.events().forEach(event -> itemSiteIds.add(event.getSiteId()));
        Mono<Map<UUID, SiteCatalogPort.SiteSnapshot>> sites = ensureSites(itemSiteIds, effective.sites(),
                bearerToken);
        Mono<List<DemandRecord>> current = records(filter.from(), filter.to(), effective);
        // The previous period may contain different activities/events, so it keeps the full scope.
        Mono<List<DemandRecord>> previous = records(filter.previousFrom(), filter.from(), scope);
        Mono<ServicesResult> services = effective.includeServices()
                ? ms04Demand.fetch(filter, bearerToken)
                        .map(demand -> new ServicesResult(demand, false))
                        .onErrorResume(error -> {
                            log.warn("Could not fetch ms-04 demand indicators: {}", error.toString());
                            return Mono.just(new ServicesResult(emptyServices(), true));
                        })
                : Mono.just(new ServicesResult(emptyServices(), false));

        return Mono.zip(sites, current, previous, services)
                .map(tuple -> build(effective, filter, tuple.getT1(), tuple.getT2(), tuple.getT3(),
                        tuple.getT4()));
    }

    private Mono<List<DemandRecord>> records(Instant from, Instant to, Scope scope) {
        var activityIds = scope.activities().stream().map(Activity::getId).toList();
        var eventIds = scope.includeServices()
                ? scope.events().stream().map(Event::getId).toList()
                : null;
        var query = new DemandQuery(from, to, scope.guideId(), activityIds, eventIds);
        if (query.hasNoItems()) {
            return Mono.just(List.of());
        }
        return demandQuery.find(query).collectList();
    }

    private DemandIndicators build(
            Scope scope,
            DemandFilter filter,
            Map<UUID, SiteCatalogPort.SiteSnapshot> sites,
            List<DemandRecord> current,
            List<DemandRecord> previous,
            ServicesResult services
    ) {
        var localSummary = summary(current);
        var localPrevious = summary(previous);
        var summary = mergeSummary(localSummary, services.demand().summary());
        var previousSummary = mergeSummary(localPrevious, services.demand().comparison().previous());
        var comparison = comparison(summary, previousSummary);
        long totalPeople = summary.people();

        var activityStats = statsByItem(current, true);
        var eventStats = statsByItem(current, false);
        var activities = activities(scope.activities(), activityStats, sites, totalPeople);
        var events = events(scope.events(), eventStats, sites, totalPeople);
        var siteDemand = sites(scope, activityStats, eventStats, sites, totalPeople);
        var zoneDemand = zones(scope, activityStats, eventStats, sites, totalPeople);

        var mergedSites = mergeSites(siteDemand, services.demand().sites(), totalPeople);
        var mergedZones = mergeZones(zoneDemand, services.demand().zones(), totalPeople);
        var mergedTimeline = mergeTimeline(timeline(current, filter), services.demand().timeline());
        var mergedWeekdays = mergeDistributions(weekdays(current), services.demand().weekdays());
        var mergedHours = mergeDistributions(hours(current), services.demand().hours());
        var serviceDemand = services.demand().services().stream()
                .map(service -> withShare(service, totalPeople))
                .toList();

        return new DemandIndicators(
                filter.from(),
                filter.to(),
                filter.granularity(),
                zoneId.getId(),
                scope.label(),
                summary,
                comparison,
                mergedTimeline,
                mergedWeekdays,
                mergedHours,
                mergedZones,
                mergedSites,
                activities,
                events,
                serviceDemand,
                services.partial(),
                Instant.now(clock));
    }

    private List<ActivityDemand> activities(
            List<Activity> activities,
            Map<UUID, GroupTotals> stats,
            Map<UUID, SiteCatalogPort.SiteSnapshot> sites,
            long totalPeople
    ) {
        return activities.stream()
                .map(activity -> {
                    var site = site(activity.getSiteId(), sites);
                    var group = stats.getOrDefault(activity.getId(), new GroupTotals());
                    long capacity = Math.max(activity.getMaxCapacity(), 0);
                    return new ActivityDemand(
                            activity.getId(),
                            activity.getName(),
                            activity.getCategory(),
                            activity.getSiteId(),
                            siteName(site, activity.getSiteId()),
                            site == null ? null : site.zoneId(),
                            site == null ? null : site.zoneName(),
                            activity.getGuideId(),
                            group.reservations,
                            group.people,
                            money(group.revenue),
                            share(group.people, totalPeople),
                            capacity,
                            occupancy(group.people, capacity));
                })
                .sorted(Comparator.comparingLong(ActivityDemand::people).reversed()
                        .thenComparing(ActivityDemand::name, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<EventDemand> events(
            List<Event> events,
            Map<UUID, GroupTotals> stats,
            Map<UUID, SiteCatalogPort.SiteSnapshot> sites,
            long totalPeople
    ) {
        return events.stream()
                .map(event -> {
                    var site = site(event.getSiteId(), sites);
                    var group = stats.getOrDefault(event.getId(), new GroupTotals());
                    long capacity = Math.max(event.getMaxCapacity(), 0);
                    return new EventDemand(
                            event.getId(),
                            event.getName(),
                            event.getCategory(),
                            event.getStatus(),
                            event.getSiteId(),
                            siteName(site, event.getSiteId(), event.getSiteName()),
                            site == null ? null : site.zoneId(),
                            site == null ? null : site.zoneName(),
                            event.getManagerId(),
                            group.reservations,
                            group.people,
                            money(group.revenue),
                            share(group.people, totalPeople),
                            capacity,
                            occupancy(group.people, capacity),
                            event.getExpectedAttendance(),
                            event.getExpectedAttendance() <= 0
                                    ? 0.0
                                    : (double) group.people / event.getExpectedAttendance());
                })
                .sorted(Comparator.comparingLong(EventDemand::people).reversed()
                        .thenComparing(EventDemand::name, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<SiteDemand> sites(
            Scope scope,
            Map<UUID, GroupTotals> activityStats,
            Map<UUID, GroupTotals> eventStats,
            Map<UUID, SiteCatalogPort.SiteSnapshot> sites,
            long totalPeople
    ) {
        Map<UUID, DemandGroup> groups = new LinkedHashMap<>();
        for (Activity activity : scope.activities()) {
            var site = site(activity.getSiteId(), sites);
            var group = groups.computeIfAbsent(activity.getSiteId(), id -> new DemandGroup(
                    siteName(site, id), site == null ? null : site.zoneId(),
                    site == null ? null : site.zoneName()));
            group.add(activityStats.getOrDefault(activity.getId(), new GroupTotals()),
                    activity.getMaxCapacity());
        }
        for (Event event : scope.events()) {
            var site = site(event.getSiteId(), sites);
            var group = groups.computeIfAbsent(event.getSiteId(), id -> new DemandGroup(
                    siteName(site, id, event.getSiteName()), site == null ? null : site.zoneId(),
                    site == null ? null : site.zoneName()));
            group.add(eventStats.getOrDefault(event.getId(), new GroupTotals()), event.getMaxCapacity());
        }
        return groups.entrySet().stream()
                .map(entry -> {
                    DemandGroup group = entry.getValue();
                    return new SiteDemand(
                            entry.getKey(),
                            group.name,
                            group.zoneId,
                            group.zoneName,
                            group.reservations,
                            group.people,
                            money(group.revenue),
                            share(group.people, totalPeople),
                            group.capacity,
                            occupancy(group.people, group.capacity));
                })
                .sorted(Comparator.comparingLong(SiteDemand::people).reversed()
                        .thenComparing(SiteDemand::siteName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<ZoneDemand> zones(
            Scope scope,
            Map<UUID, GroupTotals> activityStats,
            Map<UUID, GroupTotals> eventStats,
            Map<UUID, SiteCatalogPort.SiteSnapshot> sites,
            long totalPeople
    ) {
        Map<UUID, DemandGroup> groups = new LinkedHashMap<>();
        for (Activity activity : scope.activities()) {
            var site = site(activity.getSiteId(), sites);
            var zoneKey = site == null || site.zoneId() == null ? null : site.zoneId();
            var group = groups.computeIfAbsent(zoneKey, id -> new DemandGroup(
                    site == null ? null : site.zoneName(), id,
                    site == null ? null : site.zoneName()));
            group.zoneStatus = site == null ? null : site.zoneStatus();
            group.add(activityStats.getOrDefault(activity.getId(), new GroupTotals()),
                    activity.getMaxCapacity());
        }
        for (Event event : scope.events()) {
            var site = site(event.getSiteId(), sites);
            var zoneKey = site == null || site.zoneId() == null ? null : site.zoneId();
            var group = groups.computeIfAbsent(zoneKey, id -> new DemandGroup(
                    site == null ? null : site.zoneName(), id,
                    site == null ? null : site.zoneName()));
            group.zoneStatus = site == null ? null : site.zoneStatus();
            group.add(eventStats.getOrDefault(event.getId(), new GroupTotals()), event.getMaxCapacity());
        }
        return groups.entrySet().stream()
                .map(entry -> {
                    DemandGroup group = entry.getValue();
                    return new ZoneDemand(
                            entry.getKey(),
                            group.zoneName == null ? "Sin zona" : group.zoneName,
                            group.zoneStatus,
                            group.reservations,
                            group.people,
                            money(group.revenue),
                            share(group.people, totalPeople),
                            group.capacity,
                            occupancy(group.people, group.capacity));
                })
                .sorted(Comparator.comparingLong(ZoneDemand::people).reversed()
                        .thenComparing(ZoneDemand::zoneName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private static Map<UUID, GroupTotals> statsByItem(List<DemandRecord> records, boolean activity) {
        Map<UUID, GroupTotals> stats = new HashMap<>();
        for (DemandRecord record : records) {
            if (record.isCancelled()) {
                continue;
            }
            var isActivity = record.itemType()
                    == com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType.ACTIVITY;
            if (isActivity == activity) {
                stats.computeIfAbsent(record.itemId(), ignored -> new GroupTotals()).add(record);
            }
        }
        return stats;
    }

    private static DemandSummary summary(List<DemandRecord> records) {
        Set<UUID> active = new HashSet<>();
        Set<UUID> cancelled = new HashSet<>();
        Set<UUID> completed = new HashSet<>();
        Set<UUID> tourists = new HashSet<>();
        long people = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (DemandRecord record : records) {
            if (record.isCancelled()) {
                cancelled.add(record.reservationId());
                continue;
            }
            active.add(record.reservationId());
            if (record.isCompleted()) {
                completed.add(record.reservationId());
            }
            tourists.add(record.touristId());
            people += record.people();
            revenue = revenue.add(record.itemTotal() == null ? BigDecimal.ZERO : record.itemTotal());
        }
        long reservations = active.size();
        long denominator = reservations + cancelled.size();
        return new DemandSummary(
                reservations,
                people,
                money(revenue),
                cancelled.size(),
                completed.size(),
                tourists.size(),
                denominator == 0 ? 0.0 : (double) cancelled.size() / denominator,
                reservations == 0 ? 0.0 : (double) people / reservations);
    }

    private static DemandSummary mergeSummary(DemandSummary first, DemandSummary second) {
        if (second == null) {
            return first;
        }
        long reservations = first.reservations() + second.reservations();
        long people = first.people() + second.people();
        long cancelled = first.cancelledReservations() + second.cancelledReservations();
        long completed = first.completedReservations() + second.completedReservations();
        long denominator = reservations + cancelled;
        return new DemandSummary(
                reservations,
                people,
                money(first.revenue().add(second.revenue())),
                cancelled,
                completed,
                first.uniqueTourists() + second.uniqueTourists(),
                denominator == 0 ? 0.0 : (double) cancelled / denominator,
                reservations == 0 ? 0.0 : (double) people / reservations);
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
            for (YearMonth cursor = YearMonth.from(filter.from().atZone(zoneId));
                 !cursor.isAfter(YearMonth.from(lastDay)); cursor = cursor.plusMonths(1)) {
                buckets.put(cursor.toString(), new GroupTotals());
            }
        } else {
            for (LocalDate cursor = filter.from().atZone(zoneId).toLocalDate();
                 !cursor.isAfter(lastDay); cursor = cursor.plusDays(1)) {
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
                        entry.getValue().reservations,
                        entry.getValue().people,
                        money(entry.getValue().revenue)))
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
                        entry.getValue().reservations,
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
                    groups[hour].reservations,
                    groups[hour].people));
        }
        return distribution;
    }

    private static List<DemandBucket> mergeTimeline(List<DemandBucket> local, List<DemandBucket> services) {
        Map<String, GroupTotals> groups = new LinkedHashMap<>();
        for (DemandBucket bucket : local) {
            var group = groups.computeIfAbsent(bucket.period(), ignored -> new GroupTotals());
            group.add(bucket.reservations(), bucket.people(), bucket.revenue());
        }
        for (DemandBucket bucket : services) {
            var group = groups.computeIfAbsent(bucket.period(), ignored -> new GroupTotals());
            group.add(bucket.reservations(), bucket.people(), bucket.revenue());
        }
        return groups.entrySet().stream()
                .map(entry -> new DemandBucket(entry.getKey(), entry.getValue().reservations,
                        entry.getValue().people, money(entry.getValue().revenue)))
                .toList();
    }

    private static List<DemandDistribution> mergeDistributions(
            List<DemandDistribution> local,
            List<DemandDistribution> services
    ) {
        Map<String, DemandDistribution> groups = new LinkedHashMap<>();
        for (DemandDistribution distribution : local) {
            groups.put(distribution.key(), distribution);
        }
        for (DemandDistribution distribution : services) {
            var current = groups.get(distribution.key());
            groups.put(distribution.key(), current == null
                    ? distribution
                    : new DemandDistribution(distribution.key(), distribution.label(),
                    current.reservations() + distribution.reservations(),
                    current.people() + distribution.people()));
        }
        return List.copyOf(groups.values());
    }

    private static List<SiteDemand> mergeSites(
            List<SiteDemand> local,
            List<SiteDemand> services,
            long totalPeople
    ) {
        Map<UUID, SiteDemand> groups = new LinkedHashMap<>();
        for (SiteDemand site : local) {
            groups.put(site.siteId(), site);
        }
        for (SiteDemand site : services) {
            var current = groups.get(site.siteId());
            if (current == null) {
                groups.put(site.siteId(), site);
            } else {
                groups.put(site.siteId(), new SiteDemand(
                        site.siteId(),
                        current.siteName() == null ? site.siteName() : current.siteName(),
                        current.zoneId() == null ? site.zoneId() : current.zoneId(),
                        current.zoneName() == null ? site.zoneName() : current.zoneName(),
                        current.reservations() + site.reservations(),
                        current.people() + site.people(),
                        money(current.revenue().add(site.revenue())),
                        share(current.people() + site.people(), totalPeople),
                        current.capacityPeople() + site.capacityPeople(),
                        occupancy(current.people() + site.people(),
                                current.capacityPeople() + site.capacityPeople())));
            }
        }
        return groups.values().stream()
                .map(site -> new SiteDemand(site.siteId(), site.siteName(), site.zoneId(),
                        site.zoneName(), site.reservations(), site.people(), site.revenue(),
                        share(site.people(), totalPeople), site.capacityPeople(),
                        occupancy(site.people(), site.capacityPeople())))
                .sorted(Comparator.comparingLong(SiteDemand::people).reversed()
                        .thenComparing(SiteDemand::siteName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private static List<ZoneDemand> mergeZones(
            List<ZoneDemand> local,
            List<ZoneDemand> services,
            long totalPeople
    ) {
        Map<UUID, ZoneDemand> groups = new LinkedHashMap<>();
        for (ZoneDemand zone : local) {
            groups.put(zone.zoneId(), zone);
        }
        for (ZoneDemand zone : services) {
            var current = groups.get(zone.zoneId());
            if (current == null) {
                groups.put(zone.zoneId(), zone);
            } else {
                groups.put(zone.zoneId(), new ZoneDemand(
                        zone.zoneId(),
                        current.zoneName() == null ? zone.zoneName() : current.zoneName(),
                        current.zoneStatus() == null ? zone.zoneStatus() : current.zoneStatus(),
                        current.reservations() + zone.reservations(),
                        current.people() + zone.people(),
                        money(current.revenue().add(zone.revenue())),
                        share(current.people() + zone.people(), totalPeople),
                        current.capacityPeople() + zone.capacityPeople(),
                        occupancy(current.people() + zone.people(),
                                current.capacityPeople() + zone.capacityPeople())));
            }
        }
        return groups.values().stream()
                .map(zone -> new ZoneDemand(zone.zoneId(), zone.zoneName(), zone.zoneStatus(),
                        zone.reservations(), zone.people(), zone.revenue(),
                        share(zone.people(), totalPeople), zone.capacityPeople(),
                        occupancy(zone.people(), zone.capacityPeople())))
                .sorted(Comparator.comparingLong(ZoneDemand::people).reversed()
                        .thenComparing(ZoneDemand::zoneName, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private static ServiceDemand withShare(ServiceDemand service, long totalPeople) {
        return new ServiceDemand(service.serviceId(), service.serviceName(), service.serviceType(),
                service.siteId(), service.siteName(), service.zoneId(), service.zoneName(),
                service.reservations(), service.people(), service.revenue(),
                share(service.people(), totalPeople), service.capacityPeople(),
                service.occupancyRate());
    }

    private Mono<Map<UUID, SiteCatalogPort.SiteSnapshot>> ensureSites(
            Collection<UUID> siteIds,
            Map<UUID, SiteCatalogPort.SiteSnapshot> known,
            String bearerToken
    ) {
        var missing = siteIds.stream().filter(id -> id != null && !known.containsKey(id)).distinct().toList();
        if (missing.isEmpty()) {
            return Mono.just(known);
        }
        return Flux.fromIterable(missing)
                .flatMap(id -> siteCatalog.findById(id, bearerToken)
                        .onErrorResume(error -> Mono.empty())
                        .defaultIfEmpty(new SiteCatalogPort.SiteSnapshot(id, null, null)))
                .collectMap(SiteCatalogPort.SiteSnapshot::siteId, site -> site)
                .map(found -> {
                    var result = new LinkedHashMap<>(known);
                    result.putAll(found);
                    return result;
                });
    }

    private static SiteCatalogPort.SiteSnapshot site(
            UUID siteId,
            Map<UUID, SiteCatalogPort.SiteSnapshot> sites
    ) {
        return siteId == null ? null : sites.get(siteId);
    }

    private static boolean inPeriod(Instant startsAt, DemandFilter filter) {
        return startsAt != null && !startsAt.isBefore(filter.from()) && startsAt.isBefore(filter.to());
    }

    private static boolean matchesSiteFilter(
            UUID siteId,
            DemandFilter filter,
            Map<UUID, SiteCatalogPort.SiteSnapshot> sites
    ) {
        if (filter.siteId() != null && !filter.siteId().equals(siteId)) {
            return false;
        }
        if (filter.zoneId() == null) {
            return true;
        }
        var site = site(siteId, sites);
        return site != null && filter.zoneId().equals(site.zoneId());
    }

    private static String siteName(SiteCatalogPort.SiteSnapshot site, UUID siteId) {
        return siteName(site, siteId, null);
    }

    private static String siteName(SiteCatalogPort.SiteSnapshot site, UUID siteId, String fallback) {
        if (site != null && site.name() != null && !site.name().isBlank()) {
            return site.name();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return siteId == null ? null : siteId.toString();
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
        return capacity <= 0 ? 0.0 : Math.min(1.0, (double) people / capacity);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private Ms04DemandPort.ServicesDemand emptyServices() {
        var empty = DemandSummary.empty();
        return new Ms04DemandPort.ServicesDemand(empty,
                new DemandComparison(empty, empty, 0, 0, 0, 0),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private record Scope(
            String label,
            UUID guideId,
            List<Activity> activities,
            List<Event> events,
            Map<UUID, SiteCatalogPort.SiteSnapshot> sites,
            boolean includeServices
    ) {
    }

    private record Items(List<Activity> activities, List<Event> events) {
    }

    private record ServicesResult(Ms04DemandPort.ServicesDemand demand, boolean partial) {
    }

    private static final class GroupTotals {
        private final Set<UUID> reservationIds = new HashSet<>();
        private long reservations;
        private long people;
        private BigDecimal revenue = BigDecimal.ZERO;

        void add(DemandRecord record) {
            if (reservationIds.add(record.reservationId())) {
                reservations++;
            }
            people += record.people();
            revenue = revenue.add(record.itemTotal() == null ? BigDecimal.ZERO : record.itemTotal());
        }

        void add(long reservations, long people, BigDecimal revenue) {
            this.reservations += reservations;
            this.people += people;
            this.revenue = this.revenue.add(revenue == null ? BigDecimal.ZERO : revenue);
        }
    }

    private static final class DemandGroup {
        private String name;
        private UUID zoneId;
        private String zoneName;
        private String zoneStatus;
        private long reservations;
        private long people;
        private BigDecimal revenue = BigDecimal.ZERO;
        private long capacity;

        private DemandGroup(String name, UUID zoneId, String zoneName) {
            this.name = name;
            this.zoneId = zoneId;
            this.zoneName = zoneName;
        }

        void add(GroupTotals totals, int itemCapacity) {
            reservations += totals.reservations;
            people += totals.people;
            revenue = revenue.add(totals.revenue);
            capacity += Math.max(itemCapacity, 0);
        }
    }
}
