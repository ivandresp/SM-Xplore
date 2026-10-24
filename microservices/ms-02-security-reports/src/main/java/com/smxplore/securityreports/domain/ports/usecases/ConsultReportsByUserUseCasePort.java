package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.SecurityReport;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ConsultReportsByUserUseCasePort {
    Flux<SecurityReport> handle(UUID userId);
}
