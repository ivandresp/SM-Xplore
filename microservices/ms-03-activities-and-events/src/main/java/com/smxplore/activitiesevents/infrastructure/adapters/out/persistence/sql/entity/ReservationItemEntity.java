package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Table("reservation_items")
public record ReservationItemEntity(
        @Id
        UUID id,
        UUID reservationId,
        UUID itemId,
        ReservationItemType type,
        BigDecimal price
) {
}
