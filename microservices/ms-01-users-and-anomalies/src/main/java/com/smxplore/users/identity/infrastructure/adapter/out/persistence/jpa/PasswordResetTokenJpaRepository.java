package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {

    Optional<PasswordResetTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update PasswordResetTokenJpaEntity token
               set token.usedAt = :now
             where token.userId = :userId
               and token.usedAt is null
            """)
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
