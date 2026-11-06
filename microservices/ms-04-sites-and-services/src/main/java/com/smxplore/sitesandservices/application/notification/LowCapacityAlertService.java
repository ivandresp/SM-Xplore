package com.smxplore.sitesandservices.application.notification;

import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationType;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.ports.out.NotificationDeliveryPort;
import com.smxplore.sitesandservices.domain.ports.out.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * RF66: avisa al proveedor del servicio y al gestor del sitio cuando una reserva deja el
 * cupo restante por debajo del umbral configurado. El aviso solo se emite al cruzar el
 * umbral (antes estaba por encima o en el umbral, después queda por debajo) para no
 * inundar la bandeja con cada reserva posterior.
 *
 * <p>Se apoya en el mismo patrón que {@link NotificationCreateService}: identificadores
 * determinísticos, {@code saveIfAbsent} en la bandeja Mongo y entrega best-effort por los
 * canales configurados. Un fallo del canal o de la bandeja no debe tumbar la creación de
 * la reserva, por eso el flujo completo degrada a un warning.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class LowCapacityAlertService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final NotificationRepositoryPort notificationRepository;
    private final List<NotificationDeliveryPort> notificationDeliveries;
    private final LowCapacityPolicy policy;
    private final Clock clock;

    public Mono<Void> handle(
            Reservation reservation,
            UUID eventId,
            List<LowCapacitySnapshot> snapshots
    ) {
        if (!policy.enabled() || snapshots == null || snapshots.isEmpty()
                || reservation == null || eventId == null) {
            return Mono.empty();
        }

        List<Notification> notifications = new ArrayList<>();
        for (LowCapacitySnapshot snapshot : snapshots) {
            if (!crossesThreshold(
                    snapshot.capacity(),
                    snapshot.remainingBefore(),
                    snapshot.remainingAfter(),
                    policy.thresholdPercent())) {
                continue;
            }
            for (Recipient recipient : recipients(snapshot)) {
                notifications.add(notification(reservation, eventId, snapshot, recipient));
            }
        }
        if (notifications.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(notifications)
                .concatMap(notification -> notificationRepository.saveIfAbsent(notification)
                        .flatMap(inserted -> inserted ? delivery(notification) : Mono.empty()))
                .then()
                .onErrorResume(exception -> {
                    log.warn(
                            "Low capacity alerts for reservation {} could not be stored: {}",
                            reservation.reservationId(),
                            exception.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Indica si la reserva acaba de cruzar el umbral: el cupo disponible antes de la
     * reserva estaba en el umbral o por encima y el cupo posterior queda por debajo.
     * Si ya estaba por debajo, no se vuelve a avisar.
     */
    public static boolean crossesThreshold(
            int capacity,
            int remainingBefore,
            int remainingAfter,
            int thresholdPercent
    ) {
        if (capacity < 1 || thresholdPercent < 1) {
            return false;
        }
        int threshold = (int) Math.ceil(capacity * thresholdPercent / 100.0);
        threshold = Math.max(1, Math.min(capacity, threshold));
        return remainingBefore >= threshold && remainingAfter < threshold;
    }

    /**
     * Deduplica por persona: si el gestor del sitio es también el proveedor del servicio
     * recibe un único aviso con el rol de proveedor, para no duplicar la notificación.
     */
    private Set<Recipient> recipients(LowCapacitySnapshot snapshot) {
        Set<Recipient> recipients = new LinkedHashSet<>();
        if (snapshot.providerId() != null) {
            recipients.add(new Recipient(snapshot.providerId(), UserRole.SERVICE_PROVIDER));
        }
        if (snapshot.managerId() != null
                && !snapshot.managerId().equals(snapshot.providerId())) {
            recipients.add(new Recipient(snapshot.managerId(), UserRole.DESTINATION_MANAGER));
        }
        return recipients;
    }

    private Notification notification(
            Reservation reservation,
            UUID eventId,
            LowCapacitySnapshot snapshot,
            Recipient recipient
    ) {
        NotificationType type = NotificationType.LOW_CAPACITY_ALERT;
        UUID notificationId = UUID.nameUUIDFromBytes((
                eventId + "|" + recipient.id() + "|" + recipient.role() + "|"
                        + type.name() + "|" + snapshot.serviceId()
        ).getBytes(StandardCharsets.UTF_8));
        Notification notification = Notification.builder()
                .notificationId(notificationId)
                .recipientId(recipient.id())
                .recipientRole(recipient.role())
                .reservationId(reservation.reservationId())
                .eventId(eventId)
                .type(type)
                .title("Alerta de baja capacidad")
                .message(message(snapshot))
                .createdAt(Instant.now(clock))
                .build();
        notification.validate();
        return notification;
    }

    private String message(LowCapacitySnapshot snapshot) {
        String date = snapshot.bookedFor() == null
                ? "la fecha reservada"
                : DATE_FORMAT.format(snapshot.bookedFor());
        int remainingPercent = Math.round(snapshot.remainingAfter() * 100f / snapshot.capacity());
        return "El servicio " + snapshot.serviceName() + " para " + date
                + " quedó con " + snapshot.remainingAfter() + " de " + snapshot.capacity()
                + " plazas disponibles (" + remainingPercent + "%).";
    }

    private Mono<Void> delivery(Notification notification) {
        return Flux.fromIterable(notificationDeliveries)
                .concatMap(port -> port.deliver(notification)
                        .onErrorResume(exception -> {
                            log.warn(
                                    "Low capacity notification {} was stored but not delivered: {}",
                                    notification.notificationId(),
                                    exception.getMessage());
                            return Mono.empty();
                        }))
                .then();
    }

    private record Recipient(UUID id, UserRole role) {
    }
}
