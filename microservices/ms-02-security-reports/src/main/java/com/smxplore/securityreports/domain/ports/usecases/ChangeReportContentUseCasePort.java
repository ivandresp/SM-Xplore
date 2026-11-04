package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.SecurityReport;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ChangeReportContentUseCasePort {
    Mono<SecurityReport> handle(UUID reportId, String title, String description);
}
