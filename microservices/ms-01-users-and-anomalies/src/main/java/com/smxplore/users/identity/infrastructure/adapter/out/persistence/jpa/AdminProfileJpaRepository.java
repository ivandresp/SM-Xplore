package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminProfileJpaRepository extends JpaRepository<AdminProfileJpaEntity, UUID> {}
