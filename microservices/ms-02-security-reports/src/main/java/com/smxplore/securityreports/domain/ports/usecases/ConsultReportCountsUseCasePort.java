package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ConsultReportCountsUseCasePort {
    Mono<Map<SecurityReportStatus, Long>> handle();
}
