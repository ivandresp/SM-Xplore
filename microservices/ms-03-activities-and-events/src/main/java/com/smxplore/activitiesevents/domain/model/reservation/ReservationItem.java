package com.smxplore.activitiesevents.domain.model.reservation;

import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemException;
import com.smxplore.activitiesevents.domain.exceptions.reservation.InvalidReservationItemPriceException;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Builder(toBuilder = true)
@Getter
public class ReservationItem {
    @Builder.Default
    private final UUID id = UUID.randomUUID();
    private final UUID reservationId;
    private final UUID itemId;
    private final ReservationItemType type;
    private BigDecimal price;

    public void validate() {
        validateItemId();
        validateType();
        validatePrice(price);
    }

    public void changePrice(BigDecimal price) {
        validatePrice(price);
        this.price = price;
    }

    private void validateItemId() {
        if (itemId == null) {
            throw new InvalidReservationItemException("Item id is mandatory.");
        }
    }

    private void validateType() {
        if (type == null) {
            throw new InvalidReservationItemException("Item type is mandatory.");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidReservationItemPriceException("Price must not be null or negative.");
        }
    }
}
