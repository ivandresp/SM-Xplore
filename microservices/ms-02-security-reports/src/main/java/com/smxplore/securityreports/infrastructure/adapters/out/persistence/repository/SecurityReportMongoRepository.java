package com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository;

import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.SecurityReportDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface SecurityReportMongoRepository extends ReactiveMongoRepository<SecurityReportDocument, String> {
}
