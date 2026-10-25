package com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto;

import com.smxplore.activitiesevents.domain.model.reservation.PaymentMethod;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * El total no se recibe del cliente: empieza en cero y se recalcula como la
 * suma de los precios de los items al agregarlos.
 */
public record ReservationRequest(
        @NotNull UUID touristId,
        @Min(1) int numberOfPeople,
        @NotNull PaymentMethod paymentMethod,
        @NotNull @Future Instant bookedFor,
        String notes
) {
}
