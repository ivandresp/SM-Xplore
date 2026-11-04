package com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper;

import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationItem;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.CreateReservationItemRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.reservation.CreateReservationRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReservationRequestMapper {

    public Reservation toDomain(CreateReservationRequest request) {
        List<ReservationItem> items = request.items() == null
                ? List.of()
                : request.items().stream()
                        .map(this::toItem)
                        .toList();
        return Reservation.builder()
                .bookedFor(request.bookedFor())
                .notes(request.notes())
                .items(items)
                .build();
    }

    private ReservationItem toItem(CreateReservationItemRequest request) {
        if (request == null) {
            return null;
        }
        return ReservationItem.builder()
                .serviceId(request.serviceId())
                .people(request.people())
                .build();
    }
}
