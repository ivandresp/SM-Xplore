package com.smxplore.sitesandservices.application.catalog;

import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.ports.in.GetCategoryUseCase;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class CategoryGetService implements GetCategoryUseCase {

    private final CatalogLookup lookup;

    @Override
    public Mono<Category> handle(UUID categoryId) {
        return lookup.category(categoryId);
    }
}
