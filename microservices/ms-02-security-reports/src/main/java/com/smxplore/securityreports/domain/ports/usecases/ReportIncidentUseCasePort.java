package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.UserRef;
import reactor.core.publisher.Mono;

public interface ReportIncidentUseCasePort {
    Mono<SecurityReport> handle(UserRef user, SecurityReport report);
}
