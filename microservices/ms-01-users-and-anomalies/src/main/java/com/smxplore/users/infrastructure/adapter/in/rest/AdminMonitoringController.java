package com.smxplore.users.infrastructure.adapter.in.rest;

import com.smxplore.users.application.dto.AdminOverviewView;
import com.smxplore.users.application.dto.SystemAvailability;
import com.smxplore.users.application.port.in.GetAdminOverviewUseCase;
import com.smxplore.users.application.port.in.GetSystemAvailabilityUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin/monitoring")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMonitoringController {

    private final GetAdminOverviewUseCase getAdminOverview;
    private final GetSystemAvailabilityUseCase getSystemAvailability;

    public AdminMonitoringController(
            GetAdminOverviewUseCase getAdminOverview, GetSystemAvailabilityUseCase getSystemAvailability) {
        this.getAdminOverview = getAdminOverview;
        this.getSystemAvailability = getSystemAvailability;
    }

    @GetMapping("/overview")
    public Mono<AdminOverviewView> overview() {
        return getAdminOverview.get();
    }

    @GetMapping("/availability")
    public Mono<SystemAvailability> availability() {
        return getSystemAvailability.get();
    }
}
