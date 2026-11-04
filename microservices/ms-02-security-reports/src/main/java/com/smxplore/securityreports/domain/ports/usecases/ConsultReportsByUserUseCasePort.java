package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConsultReportsByUserUseCasePort {
    Mono<Page<SecurityReport>> handle(UUID userId, PageRequest pageRequest);
}
