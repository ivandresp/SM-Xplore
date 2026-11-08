package com.smxplore.sitesandservices.application.catalog;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.ports.in.UpdateCategoryUseCase;
import com.smxplore.sitesandservices.domain.ports.out.CategoryRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
public class CategoryUpdateService implements UpdateCategoryUseCase {

    /** Tope de profundidad de la cadena de padres para detectar ciclos (MS04-01). */
    private static final int MAX_PARENT_DEPTH = 50;

    private final CategoryRepositoryPort categoryRepository;
    private final SiteRepositoryPort siteRepository;
    private final ServiceRepositoryPort serviceRepository;
    private final CatalogLookup lookup;
    private final CatalogAccess access;
    private final TransactionalOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<Category> handle(Category category, Actor actor, String correlationId) {
        // La taxonomia es global: solo ADMIN, y la comprobacion va antes de revelar existencia.
        return access.requireTaxonomyAdmin(actor)
                .then(Mono.defer(() -> lookup.category(category.categoryId())))
                .then(Mono.defer(() -> validateParent(category.parentId(), category.categoryId())))
                .then(Mono.defer(() -> {
                    Category updated = category.toBuilder()
                            .updatedAt(Instant.now(clock))
                            .build();
                    updated.validate();
                    return save(updated, correlationId);
                }));
    }

    /**
     * Recorre la cadena completa de padres con un tope de profundidad. Un ciclo se detecta
     * cuando un id se repite o cuando la cadena vuelve a la categoria que se esta guardando.
     */
    private Mono<Void> validateParent(UUID parentId, UUID categoryId) {
        if (parentId == null) {
            return Mono.empty();
        }
        if (parentId.equals(categoryId)) {
            return Mono.error(new InvalidCatalogException("a category cannot be its own parent"));
        }
        Set<UUID> visited = new HashSet<>();
        if (categoryId != null) {
            visited.add(categoryId);
        }
        return validateAncestors(parentId, visited, 0);
    }

    private Mono<Void> validateAncestors(UUID currentId, Set<UUID> visited, int depth) {
        if (depth >= MAX_PARENT_DEPTH) {
            return Mono.error(new InvalidCatalogException(
                    "category hierarchy cannot exceed " + MAX_PARENT_DEPTH + " levels"));
        }
        if (!visited.add(currentId)) {
            return Mono.error(new InvalidCatalogException(
                    "category hierarchy contains a cycle involving " + currentId));
        }
        return lookup.category(currentId)
                .onErrorMap(ResourceNotFoundException.class,
                        notFound -> new InvalidCatalogException(
                                "parent category does not exist: " + currentId))
                .switchIfEmpty(Mono.error(new InvalidCatalogException(
                        "parent category does not exist: " + currentId)))
                .flatMap(parent -> parent.parentId() == null
                        ? Mono.<Void>empty()
                        : validateAncestors(parent.parentId(), visited, depth + 1));
    }

    private Mono<Category> save(Category category, String correlationId) {
        return outboxWriter.save(
                        categoryRepository.save(category),
                        eventFactory::categoryChanged,
                        correlationId)
                .flatMap(saved -> siteRepository.updateCategorySnapshot(
                                saved.categoryId(), saved.name())
                        .then(serviceRepository.updateCategorySnapshot(saved.categoryId(), saved.name()))
                        .thenReturn(saved));
    }
}
