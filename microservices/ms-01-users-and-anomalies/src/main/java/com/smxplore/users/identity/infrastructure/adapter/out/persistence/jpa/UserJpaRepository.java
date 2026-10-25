package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.UserStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRoleAndStatus(Role role, UserStatus status);
}
