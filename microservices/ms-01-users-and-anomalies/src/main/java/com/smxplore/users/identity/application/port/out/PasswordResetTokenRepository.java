package com.smxplore.users.identity.application.port.out;

import com.smxplore.users.identity.domain.model.PasswordResetToken;
import com.smxplore.users.identity.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void invalidateAllForUser(UserId userId, Instant now);
}
