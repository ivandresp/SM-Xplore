package com.smxplore.securityreports.domain.ports.repository;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SecurityReportRepositoryPort {
    Mono<SecurityReport> save(SecurityReport securityReport);

    Mono<SecurityReport> findById(UUID id);

    Mono<Page<SecurityReport>> findAllByStatus(SecurityReportStatus status, PageRequest pageRequest);

    Mono<Page<SecurityReport>> findAllByUserId(UUID userId, PageRequest pageRequest);
}
