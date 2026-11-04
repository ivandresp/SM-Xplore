package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetCategoryUseCase {

    Mono<Category> handle(UUID categoryId);
}
