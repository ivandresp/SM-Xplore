package com.smxplore.sitesandservices.application.support;

import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.review.CatalogTarget;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
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

    public Mono<Review> requireAdmin(Review review, Actor actor) {
        return actor.isAdmin()
                ? Mono.just(review)
                : Mono.error(new ForbiddenOperationException("Only an administrator can moderate reviews"));
    }

    public Mono<CatalogTarget> requireTargetOwner(CatalogTarget target, Actor actor) {
        return actor.isAdmin() || actor.owns(target.ownerId())
                ? Mono.just(target)
                : Mono.error(new ForbiddenOperationException(
                        "Only the target owner can respond to this review"));
    }
}
