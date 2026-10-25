package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface AuditLogJpaRepository
        extends JpaRepository<AuditLogJpaEntity, UUID>, JpaSpecificationExecutor<AuditLogJpaEntity> {}
