package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.AdminOverviewView;
import com.smxplore.users.application.dto.ComponentAvailability;
import com.smxplore.users.application.dto.SystemAvailability;
import com.smxplore.users.application.port.in.GetAdminOverviewUseCase;
import com.smxplore.users.application.port.out.AnomalyRepository;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.application.port.out.SystemAvailabilityProbe;
import com.smxplore.users.application.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import reactor.core.publisher.Mono;

public final class GetAdminOverviewService implements GetAdminOverviewUseCase {

    private final UserRepository users;
    private final ReviewRepository reviews;
    private final AnomalyRepository anomalies;
    private final SystemAvailabilityProbe availability;
    private final Clock clock;

    public GetAdminOverviewService(
            UserRepository users,
            ReviewRepository reviews,
            AnomalyRepository anomalies,
            SystemAvailabilityProbe availability,
            Clock clock) {
        this.users = users;
        this.reviews = reviews;
        this.anomalies = anomalies;
        this.availability = availability;
        this.clock = clock;
    }

    @Override
    public Mono<AdminOverviewView> get() {
        return Mono.zip(
                        users.statistics(),
                        reviews.statistics(),
                        anomalies.statistics(),
                        availability.checkComponents())
                .map(tuple -> {
                    Instant now = clock.instant();
                    SystemAvailability systemAvailability = new SystemAvailability(
                            tuple.getT4().stream().allMatch(ComponentAvailability::available), now, tuple.getT4());
                    return new AdminOverviewView(now, tuple.getT1(), tuple.getT2(), tuple.getT3(), systemAvailability);
                });
    }
}
