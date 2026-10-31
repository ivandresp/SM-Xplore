package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.SecurityReport;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CloseReportUseCasePort {
    Mono<SecurityReport> handle(UUID securityReportId);
}
