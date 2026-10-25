package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The price is optional: for SERVICE and ACTIVITY items it is taken from the
 * catalog (the sent value is ignored); for EVENT it is mandatory (validated by
 * the domain) until the event catalog exists.
 */
public record ReservationItemRequest(
        @NotNull UUID itemId,
        @NotNull ReservationItemType type,
        @DecimalMin(value = "0.0") @Digits(integer = 12, fraction = 2) BigDecimal price
) {
}
