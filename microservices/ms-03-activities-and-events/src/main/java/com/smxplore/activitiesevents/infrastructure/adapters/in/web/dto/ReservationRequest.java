package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * The total and the unit price are not received from the client: the price
 * comes from the catalog and the total is unit price × number of people.
 */
public record ReservationRequest(
        @NotNull UUID touristId,
        @NotNull ReservationItemType itemType,
        @NotNull UUID itemId,
        @Min(1) int numberOfPeople,
        @NotNull PaymentMethod paymentMethod,
        @NotNull @Future Instant bookedFor,
        String notes
) {
}
