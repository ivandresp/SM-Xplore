package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import reactor.core.publisher.Flux;

public interface ConsultReportsUseCasePort {
    Flux<SecurityReport> handle(SecurityReportStatus status);
}
