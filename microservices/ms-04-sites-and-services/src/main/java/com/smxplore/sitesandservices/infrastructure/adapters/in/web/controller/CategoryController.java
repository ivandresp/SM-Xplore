package com.smxplore.sitesandservices.infrastructure.adapters.in.web.controller;

import com.smxplore.sitesandservices.domain.model.catalog.CategoryFilter;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import com.smxplore.sitesandservices.domain.ports.in.CreateCategoryUseCase;
import com.smxplore.sitesandservices.domain.ports.in.GetCategoryUseCase;
import com.smxplore.sitesandservices.domain.ports.in.ListCategoriesUseCase;
import com.smxplore.sitesandservices.domain.ports.in.UpdateCategoryUseCase;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category.CategoryResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category.CreateCategoryRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.category.UpdateCategoryRequest;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.common.PageResponse;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.CatalogRequestMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper.CatalogWebMapper;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.security.CorrelationIdResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Catalog category management.")
public class CategoryController {

    private final CreateCategoryUseCase createUseCase;
    private final UpdateCategoryUseCase updateUseCase;
    private final GetCategoryUseCase getUseCase;
    private final ListCategoriesUseCase listUseCase;
    private final CatalogRequestMapper requestMapper;
    private final CatalogWebMapper webMapper;
    private final CorrelationIdResolver correlationIdResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a catalog category")
    public Mono<CategoryResponse> create(
            @Valid @RequestBody CreateCategoryRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId
    ) {
        return createUseCase.handle(
                        requestMapper.toDomain(request),
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Update a catalog category")
    public Mono<CategoryResponse> update(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId
    ) {
        return updateUseCase.handle(
                        requestMapper.toDomain(categoryId, request),
                        correlationIdResolver.resolve(correlationId))
                .map(webMapper::toResponse);
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "Get a catalog category")
    public Mono<CategoryResponse> get(@PathVariable UUID categoryId) {
        return getUseCase.handle(categoryId).map(webMapper::toResponse);
    }

    @GetMapping
    @Operation(summary = "List catalog categories")
    public Mono<PageResponse<CategoryResponse>> list(
            @RequestParam(required = false) CategoryScope scope,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        CategoryFilter filter = new CategoryFilter(scope, parentId, search, page, size);
        return listUseCase.handle(filter)
                .map(result -> PageResponse.from(result, webMapper::toResponse));
    }
}
