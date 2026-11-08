package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import reactor.core.publisher.Mono;

public interface UpdateCategoryUseCase {

    Mono<Category> handle(Category category, Actor actor, String correlationId);
}
