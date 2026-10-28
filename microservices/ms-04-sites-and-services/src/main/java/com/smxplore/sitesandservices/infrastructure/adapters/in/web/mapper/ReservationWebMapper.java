package com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper;

import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationAvailability;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationItem;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.ReservationAvailabilityResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.ReservationItemResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.ReservationResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReservationWebMapper {

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.reservationId(),
                reservation.touristId(),
                reservation.touristName(),
                reservation.status(),
                reservation.total(),
                reservation.numberOfPeople(),
                reservation.bookedFor(),
                reservation.notes(),
                items(reservation.items()),
                reservation.createdAt(),
                reservation.updatedAt(),
                reservation.confirmedAt(),
                reservation.cancelledAt(),
                reservation.cancellationReason()
        );
    }

    public ReservationAvailabilityResponse toResponse(ReservationAvailability availability) {
        return new ReservationAvailabilityResponse(
                availability.serviceId(),
                availability.serviceName(),
                availability.providerId(),
                availability.bookedFor(),
                availability.serviceDate(),
                availability.maxCapacity(),
                availability.reservedPeople(),
                availability.remainingCapacity(),
                availability.requestedPeople(),
                availability.available(),
                availability.reason()
        );
    }

    private List<ReservationItemResponse> items(List<ReservationItem> items) {
        return items.stream().map(this::toResponse).toList();
    }

    private ReservationItemResponse toResponse(ReservationItem item) {
        return new ReservationItemResponse(
                item.reservationItemId(),
                item.serviceId(),
                item.serviceName(),
                item.providerId(),
                item.providerName(),
                item.serviceType(),
                item.unitPrice(),
                item.people(),
                item.total(),
                item.position()
        );
    }
}
