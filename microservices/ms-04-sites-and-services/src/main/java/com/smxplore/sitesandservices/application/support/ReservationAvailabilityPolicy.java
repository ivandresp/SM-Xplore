package com.smxplore.sitesandservices.application.support;

import com.smxplore.sitesandservices.domain.model.common.AvailabilitySlot;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@RequiredArgsConstructor
public class ReservationAvailabilityPolicy {

    public static final String SERVICE_NOT_PUBLISHED = "SERVICE_NOT_PUBLISHED";
    public static final String SITE_NOT_AVAILABLE = "SITE_NOT_AVAILABLE";
    public static final String SITE_NOT_PUBLISHED = "SITE_NOT_PUBLISHED";
    public static final String ZONE_INACTIVE = "ZONE_INACTIVE";
    public static final String OUTSIDE_SERVICE_AVAILABILITY = "OUTSIDE_SERVICE_AVAILABILITY";
    public static final String INSUFFICIENT_CAPACITY = "INSUFFICIENT_CAPACITY";
    public static final String BOOKED_FOR_IN_PAST = "BOOKED_FOR_IN_PAST";

    private final ZoneId zoneId;

    public LocalDate serviceDate(Instant bookedFor) {
        return LocalDateTime.ofInstant(bookedFor, zoneId).toLocalDate();
    }

    public Instant dayStart(Instant bookedFor) {
        return serviceDate(bookedFor).atStartOfDay(zoneId).toInstant();
    }

    public Instant dayEnd(Instant bookedFor) {
        return serviceDate(bookedFor).plusDays(1).atStartOfDay(zoneId).toInstant();
    }

    public String availabilityProblem(Service service, Instant bookedFor) {
        if (service.status() == null || !service.status().isPublished()) {
            return SERVICE_NOT_PUBLISHED;
        }
        if (service.zoneStatus() == ZoneStatus.INACTIVE) {
            return ZONE_INACTIVE;
        }
        if (service.availability() == null || service.availability().isEmpty()) {
            return null;
        }

        ZonedDateTime local = ZonedDateTime.ofInstant(bookedFor, zoneId);
        DayOfWeek dayOfWeek = local.getDayOfWeek();
        LocalTime time = local.toLocalTime();
        boolean matches = service.availability().stream()
                .anyMatch(slot -> matches(slot, dayOfWeek, time));
        return matches ? null : OUTSIDE_SERVICE_AVAILABILITY;
    }

    /**
     * El sitio es el contenedor físico del servicio y puede despublicarse después de que
     * el servicio se publique, así que se valida en el momento de reservar.
     */
    public String siteProblem(Site site) {
        if (site == null) {
            return SITE_NOT_AVAILABLE;
        }
        if (site.status() == null || !site.status().isPublished()) {
            return SITE_NOT_PUBLISHED;
        }
        return null;
    }

    /**
     * Turno (día de la semana y franja horaria) que cubre la reserva, o {@code null}
     * cuando el servicio no declara disponibilidad.
     */
    public AvailabilitySlot slotFor(Service service, Instant bookedFor) {
        if (service.availability() == null || service.availability().isEmpty()) {
            return null;
        }
        ZonedDateTime local = ZonedDateTime.ofInstant(bookedFor, zoneId);
        DayOfWeek dayOfWeek = local.getDayOfWeek();
        LocalTime time = local.toLocalTime();
        return service.availability().stream()
                .filter(slot -> matches(slot, dayOfWeek, time))
                .findFirst()
                .orElse(null);
    }

    public Instant slotStart(Instant bookedFor, AvailabilitySlot slot) {
        return serviceDate(bookedFor).atTime(slot.opensAt()).atZone(zoneId).toInstant();
    }

    public Instant slotEnd(Instant bookedFor, AvailabilitySlot slot) {
        return serviceDate(bookedFor).atTime(slot.closesAt()).atZone(zoneId).toInstant();
    }

    private boolean matches(AvailabilitySlot slot, DayOfWeek dayOfWeek, LocalTime time) {
        return slot.dayOfWeek() == dayOfWeek
                && !time.isBefore(slot.opensAt())
                && time.isBefore(slot.closesAt());
    }
}
