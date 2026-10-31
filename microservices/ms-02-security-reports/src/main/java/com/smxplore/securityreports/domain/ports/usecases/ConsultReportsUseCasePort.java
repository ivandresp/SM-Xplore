package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import reactor.core.publisher.Mono;

public interface ConsultReportsUseCasePort {
    Mono<Page<SecurityReport>> handle(SecurityReportStatus status, PageRequest pageRequest);
}
