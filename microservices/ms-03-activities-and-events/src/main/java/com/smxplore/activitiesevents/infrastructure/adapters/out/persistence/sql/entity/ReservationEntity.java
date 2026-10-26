package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity;

import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Table("reservations")
public record ReservationEntity(
        @Id
        UUID id,
        UUID touristId,
        ReservationItemType itemType,
        UUID itemId,
        BigDecimal unitPrice,
        int numberOfPeople,
        BigDecimal total,
        PaymentMethod paymentMethod,
        ReservationStatus status,
        Instant bookedAt,
        Instant bookedFor,
        String notes,
        UUID providerId,
        UUID guideId,
        String code
) {
}
