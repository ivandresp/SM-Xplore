package com.smxplore.sitesandservices.application.catalog;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.ports.in.CreateCategoryUseCase;
import com.smxplore.sitesandservices.domain.ports.out.CategoryRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class CategoryCreateService implements CreateCategoryUseCase {

    private final CategoryRepositoryPort categoryRepository;
    private final SiteRepositoryPort siteRepository;
    private final ServiceRepositoryPort serviceRepository;
    private final CatalogLookup lookup;
    private final TransactionalOutboxWriter outboxWriter;
    private final DomainEventFactory eventFactory;
    private final Clock clock;

    @Override
    public Mono<Category> handle(Category category, String correlationId) {
        return validateParent(category.parentId(), null)
                .then(Mono.defer(() -> {
                    Instant now = Instant.now(clock);
                    Category created = category.toBuilder()
                            .categoryId(UUID.randomUUID())
                            .updatedAt(now)
                            .build();
                    created.validate();
                    return save(created, correlationId);
                }));
    }

    private Mono<Void> validateParent(UUID parentId, UUID categoryId) {
        if (parentId == null) {
            return Mono.empty();
        }
        if (parentId.equals(categoryId)) {
            return Mono.error(new InvalidCatalogException("a category cannot be its own parent"));
        }
        return lookup.category(parentId).then();
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
