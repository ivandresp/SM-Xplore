package com.smxplore.sitesandservices.application.support;

import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.ContentStatus;
import com.smxplore.sitesandservices.domain.model.review.CatalogTarget;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.service.ServiceFilter;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.site.SiteFilter;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import reactor.core.publisher.Mono;

public class CatalogAccess {

    public Mono<Actor> requireManager(Actor actor) {
        return actor.isAdmin() || actor.isGestora()
                ? Mono.just(actor)
                : Mono.error(new ForbiddenOperationException(
                        "Only a destination manager can manage this resource"));
    }

    public Mono<Actor> requireProvider(Actor actor) {
        return actor.isAdmin() || actor.isPrestadora()
                ? Mono.just(actor)
                : Mono.error(new ForbiddenOperationException(
                        "Only a service provider can manage this resource"));
    }

    public Mono<Actor> requireTourist(Actor actor) {
        return actor.isTourist()
                ? Mono.just(actor)
                : Mono.error(new ForbiddenOperationException("Only tourists can create reviews"));
    }

    public Mono<Zone> requireZoneOwner(Zone zone, Actor actor) {
        return actor.isAdmin() || actor.owns(zone.managerId())
                ? Mono.just(zone)
                : Mono.error(new ForbiddenOperationException("Only the zone owner can modify it"));
    }

    public Mono<Site> requireSiteOwner(Site site, Actor actor) {
        return actor.isAdmin() || actor.owns(site.managerId())
                ? Mono.just(site)
                : Mono.error(new ForbiddenOperationException("Only the site owner can modify it"));
    }

    public Mono<Service> requireServiceOwner(Service service, Actor actor) {
        return actor.isAdmin() || actor.owns(service.providerId())
                ? Mono.just(service)
                : Mono.error(new ForbiddenOperationException("Only the service owner can modify it"));
    }

    public Mono<Actor> requireAdmin(Actor actor) {
        return actor != null && actor.isAdmin()
                ? Mono.just(actor)
                : Mono.error(new ForbiddenOperationException("Only an administrator can moderate reviews"));
    }

    /**
     * La taxonomia de categorias es global: modificarla afecta a los snapshots de los sitios y
     * servicios de todas las gestoras, asi que solo el ADMIN puede gestionarla (MS04-01).
     */
    public Mono<Actor> requireTaxonomyAdmin(Actor actor) {
        return actor.isAdmin()
                ? Mono.just(actor)
                : Mono.error(new ForbiddenOperationException(
                        "Only an administrator can manage the global category taxonomy"));
    }

    public Mono<Review> requireAdmin(Review review, Actor actor) {
        return actor != null && actor.isAdmin()
                ? Mono.just(review)
                : Mono.error(new ForbiddenOperationException("Only an administrator can moderate reviews"));
    }

    public Mono<CatalogTarget> requireTargetOwner(CatalogTarget target, Actor actor) {
        return actor.isAdmin() || actor.owns(target.ownerId())
                ? Mono.just(target)
                : Mono.error(new ForbiddenOperationException(
                        "Only the target owner can respond to this review"));
    }

    public Mono<SiteFilter> requireSiteFilterScope(SiteFilter filter, Actor actor) {
        if (filter.managerId() == null) {
            return Mono.just(filter);
        }
        if (actor == null) {
            return Mono.error(new ForbiddenOperationException(
                    "Anonymous callers cannot filter sites by manager"));
        }
        if (actor.isAdmin()) {
            return Mono.just(filter);
        }
        if (actor.isGestora() && actor.owns(filter.managerId())) {
            return Mono.just(filter);
        }
        return Mono.error(new ForbiddenOperationException(
                "Only an administrator or the destination manager owning those sites can filter by manager"));
    }

    /**
     * MS04-02: el catalogo publico solo expone contenido {@code PUBLISHED}. Un administrador, o
     * el proveedor dueño del filtro, pueden consultar cualquier estado; el resto se fuerza a
     * publicado para que un anonimo no enumere borradores ni inactivos.
     */
    public ServiceFilter requireServiceVisibility(ServiceFilter filter, Actor actor) {
        boolean canSeeAnyStatus = actor != null
                && (actor.isAdmin()
                || (actor.isPrestadora()
                && filter.providerId() != null
                && actor.owns(filter.providerId())));
        if (canSeeAnyStatus || filter.status() == ContentStatus.PUBLISHED) {
            return filter;
        }
        return new ServiceFilter(
                filter.categoryId(),
                filter.zoneId(),
                filter.siteId(),
                filter.providerId(),
                ContentStatus.PUBLISHED,
                filter.type(),
                filter.search(),
                filter.priceMin(),
                filter.priceMax(),
                filter.page(),
                filter.size());
    }

    /**
     * MS04-02: equivalente a {@link #requireServiceVisibility} para el catalogo de sitios. Solo
     * el administrador o la gestora que filtra por su propio id ven estados no publicados.
     */
    public SiteFilter requireSiteVisibility(SiteFilter filter, Actor actor) {
        boolean canSeeAnyStatus = actor != null
                && (actor.isAdmin()
                || (actor.isGestora()
                && filter.managerId() != null
                && actor.owns(filter.managerId())));
        if (canSeeAnyStatus || filter.status() == ContentStatus.PUBLISHED) {
            return filter;
        }
        return new SiteFilter(
                filter.categoryId(),
                filter.zoneId(),
                ContentStatus.PUBLISHED,
                filter.search(),
                filter.managerId(),
                filter.page(),
                filter.size());
    }

    /**
     * MS04-02: el detalle de un servicio no publicado solo lo ve su proveedor o un administrador;
     * para el resto responde 404 como si no existiera (no se filtra la existencia del borrador).
     */
    public Mono<Service> requireServiceVisible(Service service, Actor actor) {
        if (service.status().isPublished()) {
            return Mono.just(service);
        }
        if (actor != null && (actor.isAdmin() || actor.owns(service.providerId()))) {
            return Mono.just(service);
        }
        return Mono.error(new ResourceNotFoundException("Service", service.serviceId()));
    }

    /**
     * MS04-02: el detalle de un sitio no publicado solo lo ve su gestora o un administrador.
     */
    public Mono<Site> requireSiteVisible(Site site, Actor actor) {
        if (site.status().isPublished()) {
            return Mono.just(site);
        }
        if (actor != null && (actor.isAdmin() || actor.owns(site.managerId()))) {
            return Mono.just(site);
        }
        return Mono.error(new ResourceNotFoundException("Site", site.siteId()));
    }

    /**
     * MS04-03: un servicio solo puede enlazarse a un sitio publicado, de la zona solicitada y
     * cuyo gestor sea el mismo dueño de esa zona. Evita snapshots de entidades ajenas.
     */
    public Mono<CatalogLookup.ServiceContext> requireServiceCatalogScope(
            CatalogLookup.ServiceContext context,
            Actor actor
    ) {
        Site site = context.site();
        Zone zone = context.zone();
        if (!site.status().isPublished()) {
            return Mono.error(new InvalidCatalogException(
                    "services can only be linked to published sites"));
        }
        if (!site.zoneId().equals(zone.zoneId())) {
            return Mono.error(new InvalidCatalogException(
                    "the requested zone does not belong to the site"));
        }
        if (!zone.managerId().equals(site.managerId())) {
            return Mono.error(new InvalidCatalogException(
                    "the site and its zone must belong to the same destination manager"));
        }
        return Mono.just(context);
    }

    /**
     * MS04-04: una gestora solo puede ubicar o mover sus sitios a zonas que ella administra.
     */
    public Mono<Zone> requireZoneAssignment(Zone zone, Actor actor) {
        return actor.isAdmin() || actor.owns(zone.managerId())
                ? Mono.just(zone)
                : Mono.error(new ForbiddenOperationException(
                        "Only the zone owner or an administrator can assign sites to that zone"));
    }
}
