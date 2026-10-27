package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryFilter;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import reactor.core.publisher.Mono;

public interface ListCategoriesUseCase {

    Mono<PageResult<Category>> handle(CategoryFilter filter);
}
