package com.smxplore.activitiesevents.domain.model.catalog;

/**
 * Entidades del catálogo que ms-03 expone al export feed consumido por ms-04.
 *
 * <p>Solo se declaran las entidades que este servicio posee: ms-04 añade SITES, SERVICES y
 * CATEGORIES en su propio enumerado y su propio controller.
 */
public enum CatalogEntity {
    ACTIVITIES,
    EVENTS
}
