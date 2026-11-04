package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation;

import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeReservationStatusRequest(
        @NotNull ReservationStatus status,
        @Size(max = 500) String reason
) {
}
