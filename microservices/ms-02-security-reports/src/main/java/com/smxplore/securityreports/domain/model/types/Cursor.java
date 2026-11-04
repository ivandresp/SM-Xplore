package com.smxplore.securityreports.domain.model.types;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Position of the last item of a page. It is translated into an opaque value
 * by the web adapter and back into a cursor when the client requests the next page.
 */
public record Cursor(Instant createdAt, UUID id) {
    public Cursor {
        Objects.requireNonNull(createdAt, "Cursor createdAt is mandatory.");
        Objects.requireNonNull(id, "Cursor id is mandatory.");
    }
}
