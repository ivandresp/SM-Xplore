package com.smxplore.securityreports.domain.ports.repository;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SecurityReportRepositoryPort {
    Mono<SecurityReport> save(SecurityReport securityReport);

    Mono<SecurityReport> findById(UUID id);

    Flux<SecurityReport> findAllByStatus(SecurityReportStatus status);

    Flux<SecurityReport> findAll();

    Mono<Void> changeContent(UUID reportId, String title, String description);

    Mono<Void> changeStatus(UUID reportId, SecurityReportStatus status);

    Flux<SecurityReport> findAllByUserId(UUID userId);
}
