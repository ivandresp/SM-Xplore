package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryFilter;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.ports.out.CategoryRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.CategoryDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MongoCategoryRepositoryAdapter implements CategoryRepositoryPort {

    private final ReactiveMongoTemplate mongoTemplate;
    private final MongoCatalogMapper mapper;

    @Override
    public Mono<Category> save(Category category) {
        return mongoTemplate.save(mapper.toDocument(category))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Category> findById(UUID categoryId) {
        return mongoTemplate.findById(categoryId.toString(), CategoryDocument.class)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<PageResult<Category>> findAll(CategoryFilter filter) {
        Query query = query(filter);
        Query pageQuery = Query.of(query)
                .with(PageRequest.of(filter.page(), filter.size(),
                        Sort.by(Sort.Direction.ASC, "name")));

        Mono<Long> total = mongoTemplate.count(query, CategoryDocument.class);
        Mono<java.util.List<Category>> content = mongoTemplate.find(pageQuery, CategoryDocument.class)
                .map(mapper::toDomain)
                .collectList();

        return Mono.zip(total, content)
                .map(tuple -> new PageResult<>(tuple.getT2(), filter.page(), filter.size(), tuple.getT1()));
    }

    private Query query(CategoryFilter filter) {
        Criteria criteria = new Criteria();
        if (filter.scope() != null) {
            criteria.and("scope").is(filter.scope().name());
        }
        if (filter.parentId() != null) {
            criteria.and("parentId").is(filter.parentId().toString());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            criteria.and("name").regex(Pattern.quote(filter.search().trim()), "i");
        }
        return Query.query(criteria);
    }
}
