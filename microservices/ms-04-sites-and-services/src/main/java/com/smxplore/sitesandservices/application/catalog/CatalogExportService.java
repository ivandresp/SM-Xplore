package com.smxplore.sitesandservices.application.catalog;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.catalog.CatalogEntity;
import com.smxplore.sitesandservices.domain.model.catalog.CatalogExportItem;
import com.smxplore.sitesandservices.domain.model.catalog.CatalogExportPage;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.event.EventTypes;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.ports.in.ExportCatalogUseCase;
import com.smxplore.sitesandservices.domain.ports.out.CategoryRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Feed de reindexado para ms-05: entrega páginas del catálogo usando el mismo payload
 * que publican los eventos de Kafka, de modo que la proyección se reconstruye con el
 * mismo código que consume los eventos incrementales.
 */
@RequiredArgsConstructor
public class CatalogExportService implements ExportCatalogUseCase {

    public static final int MAX_PAGE_SIZE = 200;

    private final CategoryRepositoryPort categoryRepository;
    private final SiteRepositoryPort siteRepository;
    private final ServiceRepositoryPort serviceRepository;
    private final DomainEventFactory eventFactory;
    private final CatalogAccess access;

    @Override
    public Mono<CatalogExportPage> handle(
            CatalogEntity entity,
            Instant updatedSince,
            int page,
            int size,
            Actor actor
    ) {
        validate(entity, page, size);
        return access.requireAdmin(actor)
                .then(Mono.defer(() -> export(entity, updatedSince, page, size)));
    }

    private Mono<CatalogExportPage> export(
            CatalogEntity entity,
            Instant updatedSince,
            int page,
            int size
    ) {
        return switch (entity) {
            case CATEGORIES -> categoryRepository.findUpdatedSince(updatedSince, page, size)
                    .map(result -> page(result, updatedSince,
                            item -> new CatalogExportItem(
                                    EventTypes.CATALOG_CATEGORY_CHANGED,
                                    item.categoryId(),
                                    item.updatedAt(),
                                    eventFactory.categoryPayload(item))));
            case SITES -> siteRepository.findUpdatedSince(updatedSince, page, size)
                    .map(result -> page(result, updatedSince,
                            item -> new CatalogExportItem(
                                    EventTypes.CATALOG_SITE_CHANGED,
                                    item.siteId(),
                                    item.updatedAt(),
                                    eventFactory.sitePayload(item))));
            case SERVICES -> serviceRepository.findUpdatedSince(updatedSince, page, size)
                    .map(result -> page(result, updatedSince,
                            item -> new CatalogExportItem(
                                    EventTypes.CATALOG_SERVICE_CHANGED,
                                    item.serviceId(),
                                    item.updatedAt(),
                                    eventFactory.servicePayload(item))));
        };
    }

    private static <T> CatalogExportPage page(
            PageResult<T> result,
            Instant updatedSince,
            java.util.function.Function<T, CatalogExportItem> mapper
    ) {
        long consumed = (long) (result.page() + 1) * result.size();
        return new CatalogExportPage(
                result.content().stream().map(mapper).toList(),
                result.page(),
                result.size(),
                result.total(),
                consumed < result.total(),
                updatedSince
        );
    }

    private static void validate(CatalogEntity entity, int page, int size) {
        if (entity == null) {
            throw new InvalidCatalogException("catalog entity is required");
        }
        if (page < 0) {
            throw new InvalidCatalogException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidCatalogException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
