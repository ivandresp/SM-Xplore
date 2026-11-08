package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.audit.SecurityAuditEntry;
import reactor.core.publisher.Mono;

public interface SecurityAuditRepositoryPort {

    Mono<Void> append(SecurityAuditEntry entry);
}
