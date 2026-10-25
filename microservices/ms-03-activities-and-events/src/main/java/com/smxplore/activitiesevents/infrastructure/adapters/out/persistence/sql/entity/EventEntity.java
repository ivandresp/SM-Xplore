package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("evento")
public record EventEntity(
        @Id UUID id,
        @Column("sitio_id") UUID siteId,
        @Column("sitio_nombre") String siteName,
        @Column("manager_id") UUID managerId,
        String name,
        String category,
        String status,
        BigDecimal price,
        @Column("max_capacity") int maxCapacity,
        @Column("starts_at") Instant startsAt,
        @Column("ends_at") Instant endsAt,
        String description,
        @Column("expected_attendance") int expectedAttendance
) {
}
