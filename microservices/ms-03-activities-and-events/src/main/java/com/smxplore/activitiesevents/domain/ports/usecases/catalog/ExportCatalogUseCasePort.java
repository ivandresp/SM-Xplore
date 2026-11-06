package com.smxplore.activitiesevents.domain.ports.usecases.catalog;

import com.smxplore.activitiesevents.domain.model.catalog.CatalogEntity;
import com.smxplore.activitiesevents.domain.model.catalog.CatalogExportPage;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Export feed del catálogo que ms-04 consume para sincronizar su buscador.
 *
 * <p>{@code role} llega como nombre del rol del token: la cadena de seguridad ya exige ADMIN para
 * {@code /api/v1/admin/**}, y el caso de uso lo vuelve a comprobar para que el puerto no dependa de
 * que la ruta esté bien declarada.
 */
public interface ExportCatalogUseCasePort {

    Mono<CatalogExportPage> handle(CatalogEntity entity, Instant updatedSince, int page, int size, String role);
}
