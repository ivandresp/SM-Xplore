package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.SecurityReport;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface AddReportPhotosUseCasePort {
    Mono<SecurityReport> handle(UUID reportId, Collection<String> photos);
}
