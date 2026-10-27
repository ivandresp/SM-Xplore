package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.site.Site;
import com.smxplore.sitesandservices.domain.model.site.SiteFilter;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.SiteDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MongoSiteRepositoryAdapter implements SiteRepositoryPort {

    private final ReactiveMongoTemplate mongoTemplate;
    private final MongoCatalogMapper mapper;

    @Override
    public Mono<Site> save(Site site) {
        return mongoTemplate.save(mapper.toDocument(site))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Site> findById(UUID siteId) {
        return mongoTemplate.findById(siteId.toString(), SiteDocument.class)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<PageResult<Site>> findAll(SiteFilter filter) {
        Query query = query(filter);
        Query pageQuery = Query.of(query)
                .with(PageRequest.of(filter.page(), filter.size(),
                        Sort.by(Sort.Direction.ASC, "name")));

        Mono<Long> total = mongoTemplate.count(query, SiteDocument.class);
        Mono<List<Site>> content = mongoTemplate.find(pageQuery, SiteDocument.class)
                .map(mapper::toDomain)
                .collectList();

        return Mono.zip(total, content)
                .map(tuple -> new PageResult<>(tuple.getT2(), filter.page(), filter.size(), tuple.getT1()));
    }

    @Override
    public Mono<Void> updateZoneSnapshot(UUID zoneId, String zoneName, ZoneStatus zoneStatus) {
        Query query = Query.query(Criteria.where("zone.id").is(zoneId.toString()));
        Update update = new Update()
                .set("zone.name", zoneName)
                .set("zone.status", zoneStatus.name());
        return mongoTemplate.updateMulti(query, update, SiteDocument.class).then();
    }

    @Override
    public Mono<Void> updateCategorySnapshot(UUID categoryId, String categoryName) {
        Query query = Query.query(Criteria.where("categoryId").is(categoryId.toString()));
        Update update = new Update().set("categoryName", categoryName);
        return mongoTemplate.updateMulti(query, update, SiteDocument.class).then();
    }

    @Override
    public Mono<Boolean> existsById(UUID siteId) {
        return mongoTemplate.exists(
                        Query.query(Criteria.where("_id").is(siteId.toString())),
                        SiteDocument.class);
    }

    private Query query(SiteFilter filter) {
        Criteria criteria = new Criteria();
        if (filter.categoryId() != null) {
            criteria.and("categoryId").is(filter.categoryId().toString());
        }
        if (filter.zoneId() != null) {
            criteria.and("zone.id").is(filter.zoneId().toString());
        }
        if (filter.status() != null) {
            criteria.and("status").is(filter.status().name());
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
