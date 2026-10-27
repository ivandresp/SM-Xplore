package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryFilter;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CategoryRepositoryPort {

    Mono<Category> save(Category category);

    Mono<Category> findById(UUID categoryId);

    Mono<PageResult<Category>> findAll(CategoryFilter filter);
}
