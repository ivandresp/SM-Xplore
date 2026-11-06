package com.smxplore.sitesandservices.domain.model.notification;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class NotificationContact {

    public static final int MAX_EMAIL_LENGTH = 254;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UUID userId;
    private final String email;
    private final Instant updatedAt;

    public void validate() {
        if (userId == null) {
            throw new InvalidCatalogException("notification contact userId is required");
        }
        validateEmail(email);
        if (updatedAt == null) {
            throw new InvalidCatalogException("notification contact updatedAt is required");
        }
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidCatalogException("notification contact email is required");
        }
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new InvalidCatalogException(
                    "notification contact email cannot exceed " + MAX_EMAIL_LENGTH + " characters");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidCatalogException("notification contact email is not valid: " + email);
        }
    }
}
