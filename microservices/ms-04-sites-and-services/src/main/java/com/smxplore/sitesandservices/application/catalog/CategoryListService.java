package com.smxplore.sitesandservices.application.catalog;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryFilter;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.ports.in.ListCategoriesUseCase;
import com.smxplore.sitesandservices.domain.ports.out.CategoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class CategoryListService implements ListCategoriesUseCase {

    private final CategoryRepositoryPort categoryRepository;

    @Override
    public Mono<PageResult<Category>> handle(CategoryFilter filter) {
        return categoryRepository.findAll(filter);
    }
}
