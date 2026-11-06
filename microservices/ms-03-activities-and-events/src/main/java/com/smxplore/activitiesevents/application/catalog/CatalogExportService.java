package com.smxplore.activitiesevents.application.catalog;

import com.smxplore.activitiesevents.application.outbox.ActivityChangedEventFactory;
import com.smxplore.activitiesevents.application.outbox.EventChangedEventFactory;
import com.smxplore.activitiesevents.domain.exceptions.catalog.CatalogExportNotAllowedException;
import com.smxplore.activitiesevents.domain.exceptions.catalog.InvalidCatalogExportException;
import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.catalog.CatalogEntity;
import com.smxplore.activitiesevents.domain.model.catalog.CatalogExportItem;
import com.smxplore.activitiesevents.domain.model.catalog.CatalogExportPage;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.catalog.ExportCatalogUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.function.Function;

/**
 * Export feed incremental del catálogo: entrega las actividades y eventos que cambiaron desde una
 * marca de tiempo, con el mismo payload que publican los eventos vivos.
 *
 * <p>El feed no inventa representaciones: reutiliza {@code payload(...)} de las factories de outbox
 * para que ms-04 reciba exactamente lo mismo por HTTP que por Kafka.
 */
@RequiredArgsConstructor
public class CatalogExportService implements ExportCatalogUseCasePort {

    /** Tope de página alineado con ms-04: evita que un cliente pida el catálogo entero de golpe. */
    public static final int MAX_PAGE_SIZE = 200;

    private final ActivityRepositoryPort activities;
    private final EventRepositoryPort events;
    private final ActivityChangedEventFactory activityEvents;
    private final EventChangedEventFactory eventEvents;

    @Override
    public Mono<CatalogExportPage> handle(CatalogEntity entity, Instant updatedSince, int page, int size,
                                          String role) {
        return Mono.defer(() -> {
            validate(entity, page, size);
            requireAdmin(role);
            return export(entity, updatedSince, page, size);
        });
    }

    private Mono<CatalogExportPage> export(CatalogEntity entity, Instant updatedSince, int page, int size) {
        return switch (entity) {
            case ACTIVITIES -> activities.findUpdatedSince(updatedSince, page, size)
                    .map(result -> page(result, updatedSince, activity -> {
                        var updatedAt = updatedAtOf(activity.getUpdatedAt());
                        return new CatalogExportItem(
                                ActivityChangedEventFactory.EVENT_TYPE,
                                ActivityChangedEventFactory.EVENT_VERSION,
                                activity.getId(),
                                updatedAt,
                                activityEvents.payload(activity, updatedAt));
                    }));
            case EVENTS -> events.findUpdatedSince(updatedSince, page, size)
                    .map(result -> page(result, updatedSince, event -> {
                        var updatedAt = updatedAtOf(event.getUpdatedAt());
                        return new CatalogExportItem(
                                EventChangedEventFactory.EVENT_TYPE,
                                EventChangedEventFactory.EVENT_VERSION,
                                event.getId(),
                                updatedAt,
                                eventEvents.payload(event, updatedAt));
                    }));
        };
    }

    private static <T> CatalogExportPage page(PageResult<T> result, Instant updatedSince,
                                               Function<T, CatalogExportItem> mapper) {
        var content = result.content().stream()
                .map(mapper)
                .toList();
        var consumed = (long) (result.page() + 1) * result.size();
        return new CatalogExportPage(content, result.page(), result.size(), result.total(),
                consumed < result.total(), updatedSince);
    }

    /**
     * Una fila recién leída siempre trae marca, pero un agregado construido en memoria puede no
     * tenerla; se usa el reloj como último recurso para no publicar un item inválido.
     */
    private static Instant updatedAtOf(Instant updatedAt) {
        return updatedAt == null ? Instant.now() : updatedAt;
    }

    private static void validate(CatalogEntity entity, int page, int size) {
        if (entity == null) {
            throw new InvalidCatalogExportException("catalog entity is required");
        }
        if (page < 0) {
            throw new InvalidCatalogExportException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidCatalogExportException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static void requireAdmin(String role) {
        if (!UserRole.ADMIN.name().equals(role)) {
            throw new CatalogExportNotAllowedException("catalog export requires the ADMIN role");
        }
    }
}
