package com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository;

import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.SecurityReportDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface SecurityReportMongoRepository extends ReactiveMongoRepository<SecurityReportDocument, String> {

    Flux<SecurityReportDocument> findAllByOrderByCreatedAtDesc();

    Flux<SecurityReportDocument> findAllByStatusOrderByCreatedAtDesc(SecurityReportStatus status);

    Flux<SecurityReportDocument> findAllByUser_UserIdOrderByCreatedAtDesc(String userId);
}
