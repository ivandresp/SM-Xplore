package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.CategoryDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.OutboxEventDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.ReviewDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.ServiceDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.SiteDocument;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MongoIndexInitializer {

    private final ReactiveMongoTemplate mongoTemplate;

    @PostConstruct
    void initialize() {
        List<Mono<String>> operations = List.of(
                mongoTemplate.indexOps(CategoryDocument.class)
                        .createIndex(new Index().on("scope", Sort.Direction.ASC)),
                mongoTemplate.indexOps(CategoryDocument.class)
                        .createIndex(new Index().on("parentId", Sort.Direction.ASC)),
                mongoTemplate.indexOps(SiteDocument.class)
                        .createIndex(new Index().on("status", Sort.Direction.ASC)),
                mongoTemplate.indexOps(SiteDocument.class)
                        .createIndex(new Index().on("categoryId", Sort.Direction.ASC)),
                mongoTemplate.indexOps(SiteDocument.class)
                        .createIndex(new Index().on("zone.id", Sort.Direction.ASC)),
                mongoTemplate.indexOps(SiteDocument.class)
                        .createIndex(new Index().on("updatedAt", Sort.Direction.DESC)),
                mongoTemplate.indexOps(ServiceDocument.class)
                        .createIndex(new Index().on("status", Sort.Direction.ASC)),
                mongoTemplate.indexOps(ServiceDocument.class)
                        .createIndex(new Index().on("categoryId", Sort.Direction.ASC)),
                mongoTemplate.indexOps(ServiceDocument.class)
                        .createIndex(new Index().on("zone.id", Sort.Direction.ASC)),
                mongoTemplate.indexOps(ServiceDocument.class)
                        .createIndex(new Index().on("site.id", Sort.Direction.ASC)),
                mongoTemplate.indexOps(ServiceDocument.class)
                        .createIndex(new Index().on("provider.id", Sort.Direction.ASC)),
                mongoTemplate.indexOps(ServiceDocument.class)
                        .createIndex(new Index().on("updatedAt", Sort.Direction.DESC)),
                mongoTemplate.indexOps(ReviewDocument.class)
                        .createIndex(new Index()
                                .on("touristId", Sort.Direction.ASC)
                                .on("itemType", Sort.Direction.ASC)
                                .on("itemId", Sort.Direction.ASC)
                                .unique()),
                mongoTemplate.indexOps(ReviewDocument.class)
                        .createIndex(new Index()
                                .on("itemType", Sort.Direction.ASC)
                                .on("itemId", Sort.Direction.ASC)
                                .on("status", Sort.Direction.ASC)
                                .on("createdAt", Sort.Direction.DESC)),
                mongoTemplate.indexOps(OutboxEventDocument.class)
                        .createIndex(new Index()
                                .on("status", Sort.Direction.ASC)
                                .on("nextAttemptAt", Sort.Direction.ASC)
                                .on("occurredAt", Sort.Direction.ASC))
        );
        Mono.when(operations).block(Duration.ofSeconds(30));
    }
}
