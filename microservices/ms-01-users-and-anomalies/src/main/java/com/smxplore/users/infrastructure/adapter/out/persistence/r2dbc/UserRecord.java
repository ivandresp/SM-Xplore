package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import java.time.OffsetDateTime;
import java.util.UUID;

record UserRecord(
        UUID id,
        String fullName,
        String email,
        String phone,
        String passwordHash,
        String role,
        String status,
        long authVersion,
        OffsetDateTime signedUpAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        long version) {}
