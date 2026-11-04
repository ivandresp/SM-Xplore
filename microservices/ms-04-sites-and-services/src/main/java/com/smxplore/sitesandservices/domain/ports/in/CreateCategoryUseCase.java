package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import reactor.core.publisher.Mono;

public interface CreateCategoryUseCase {

    Mono<Category> handle(Category category, String correlationId);
}
