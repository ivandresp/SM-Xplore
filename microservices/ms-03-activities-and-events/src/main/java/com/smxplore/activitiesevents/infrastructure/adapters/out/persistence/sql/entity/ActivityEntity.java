package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("actividad")
public record ActivityEntity(
        @Id UUID id,
        @Column("sitio_id") UUID siteId,
        @Column("guia_id") UUID guideId,
        String name,
        @Column("type") String category,
        String status,
        BigDecimal price,
        @Column("max_capacity") int maxCapacity,
        @Column("duration_mins") int durationMinutes,
        @Column("starts_at") Instant startsAt,
        @Column("ends_at") Instant endsAt,
        String description
) {
}
