package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.service.Service;
import com.smxplore.sitesandservices.domain.model.service.ServiceFilter;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.ServiceDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MongoServiceRepositoryAdapter implements ServiceRepositoryPort {

    private final ReactiveMongoTemplate mongoTemplate;
    private final MongoCatalogMapper mapper;

    @Override
    public Mono<Service> save(Service service) {
        return mongoTemplate.save(mapper.toDocument(service))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Service> findById(UUID serviceId) {
        return mongoTemplate.findById(serviceId.toString(), ServiceDocument.class)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<PageResult<Service>> findAll(ServiceFilter filter) {
        Query query = query(filter);
        Query pageQuery = Query.of(query)
                .with(PageRequest.of(filter.page(), filter.size(),
                        Sort.by(Sort.Direction.ASC, "name")));

        Mono<Long> total = mongoTemplate.count(query, ServiceDocument.class);
        Mono<List<Service>> content = mongoTemplate.find(pageQuery, ServiceDocument.class)
                .map(mapper::toDomain)
                .collectList();

        return Mono.zip(total, content)
                .map(tuple -> new PageResult<>(tuple.getT2(), filter.page(), filter.size(), tuple.getT1()));
    }

    @Override
    public Mono<PageResult<Service>> findUpdatedSince(Instant updatedSince, int page, int size) {
        Query query = updatedSince == null
                ? new Query()
                : Query.query(Criteria.where("updatedAt").gte(updatedSince));
        Query pageQuery = Query.of(query)
                .with(PageRequest.of(page, size,
                        Sort.by(Sort.Direction.ASC, "updatedAt").and(Sort.by(Sort.Direction.ASC, "_id"))));

        Mono<Long> total = mongoTemplate.count(query, ServiceDocument.class);
        Mono<List<Service>> content = mongoTemplate.find(pageQuery, ServiceDocument.class)
                .map(mapper::toDomain)
                .collectList();

        return Mono.zip(total, content)
                .map(tuple -> new PageResult<>(tuple.getT2(), page, size, tuple.getT1()));
    }

    @Override
    public Flux<Service> findByIds(Collection<UUID> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return Flux.empty();
        }
        List<String> ids = serviceIds.stream().distinct().map(UUID::toString).toList();
        return mongoTemplate.find(
                        Query.query(Criteria.where("_id").in(ids)),
                        ServiceDocument.class)
                .map(mapper::toDomain);
    }

    @Override
    public Flux<Service> findByScope(
            UUID providerId,
            Collection<UUID> zoneIds,
            Collection<UUID> siteIds
    ) {
        List<Criteria> alternatives = new ArrayList<>();
        if (zoneIds != null && !zoneIds.isEmpty()) {
            alternatives.add(Criteria.where("zone.id")
                    .in(zoneIds.stream().map(UUID::toString).toList()));
        }
        if (siteIds != null && !siteIds.isEmpty()) {
            alternatives.add(Criteria.where("site.id")
                    .in(siteIds.stream().map(UUID::toString).toList()));
        }
        Criteria criteria = new Criteria();
        if (providerId != null) {
            criteria.and("provider.id").is(providerId.toString());
        }
        if (!alternatives.isEmpty()) {
            criteria.orOperator(alternatives.toArray(Criteria[]::new));
        }
        return mongoTemplate.find(Query.query(criteria), ServiceDocument.class)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> updateZoneSnapshot(UUID zoneId, String zoneName, ZoneStatus zoneStatus) {
        Query query = Query.query(Criteria.where("zone.id").is(zoneId.toString()));
        Update update = new Update()
                .set("zone.name", zoneName)
                .set("zone.status", zoneStatus.name());
        return mongoTemplate.updateMulti(query, update, ServiceDocument.class).then();
    }

    @Override
    public Mono<Void> updateSiteSnapshot(UUID siteId, String siteName) {
        Query query = Query.query(Criteria.where("site.id").is(siteId.toString()));
        Update update = new Update().set("site.name", siteName);
        return mongoTemplate.updateMulti(query, update, ServiceDocument.class).then();
    }

    @Override
    public Mono<Void> updateCategorySnapshot(UUID categoryId, String categoryName) {
        Query query = Query.query(Criteria.where("categoryId").is(categoryId.toString()));
        Update update = new Update().set("categoryName", categoryName);
        return mongoTemplate.updateMulti(query, update, ServiceDocument.class).then();
    }

    @Override
    public Mono<Boolean> existsById(UUID serviceId) {
        return mongoTemplate.exists(
                Query.query(Criteria.where("_id").is(serviceId.toString())),
                ServiceDocument.class);
    }

    private Query query(ServiceFilter filter) {
        Criteria criteria = new Criteria();
        if (filter.categoryId() != null) {
            criteria.and("categoryId").is(filter.categoryId().toString());
        }
        if (filter.zoneId() != null) {
            criteria.and("zone.id").is(filter.zoneId().toString());
        }
        if (filter.siteId() != null) {
            criteria.and("site.id").is(filter.siteId().toString());
        }
        if (filter.providerId() != null) {
            criteria.and("provider.id").is(filter.providerId().toString());
        }
        if (filter.status() != null) {
            criteria.and("status").is(filter.status().name());
        }
        if (filter.type() != null && !filter.type().isBlank()) {
            criteria.and("type").is(filter.type());
        }
        if (filter.priceMin() != null || filter.priceMax() != null) {
            Criteria price = Criteria.where("price");
            if (filter.priceMin() != null) {
                price = price.gte(filter.priceMin());
            }
            if (filter.priceMax() != null) {
                price = price.lte(filter.priceMax());
            }
            criteria.andOperator(price);
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String pattern = Pattern.quote(filter.search().trim());
            criteria.orOperator(
                    Criteria.where("name").regex(pattern, "i"),
                    Criteria.where("description").regex(pattern, "i")
            );
        }
        return Query.query(criteria);
    }
}
