package com.smxplore.users.infrastructure.adapter.out.persistence.mongo;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.ReactiveIndexOperations;
import org.springframework.stereotype.Component;

/**
 * Crea los índices de la colección de reseñas. Se ejecuta al arrancar y no impide el arranque si MongoDB no
 * está disponible todavía; en ese caso deja un aviso y los índices se pueden crear al reiniciar.
 */
@Component
@ConditionalOnProperty(name = "app.reviews.index-initialization.enabled", havingValue = "true", matchIfMissing = true)
public class ReviewIndexInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewIndexInitializer.class);

    private final ReactiveMongoTemplate template;

    public ReviewIndexInitializer(ReactiveMongoTemplate template) {
        this.template = template;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ReactiveIndexOperations indexes = template.indexOps(ReviewDocument.class);
            indexes.createIndex(new Index()
                            .named("ux_reviews_tourist_target")
                            .on("touristId", Sort.Direction.ASC)
                            .on("targetType", Sort.Direction.ASC)
                            .on("targetId", Sort.Direction.ASC)
                            .unique())
                    .block(Duration.ofSeconds(10));
            indexes.createIndex(new Index()
                            .named("ix_reviews_target")
                            .on("targetType", Sort.Direction.ASC)
                            .on("targetId", Sort.Direction.ASC)
                            .on("moderationStatus", Sort.Direction.ASC)
                            .on("createdAt", Sort.Direction.DESC))
                    .block(Duration.ofSeconds(10));
            indexes.createIndex(new Index()
                            .named("ix_reviews_reported")
                            .on("reported", Sort.Direction.ASC)
                            .on("updatedAt", Sort.Direction.DESC))
                    .block(Duration.ofSeconds(10));
            indexes.createIndex(new Index()
                            .named("ix_reviews_ai_flagged")
                            .on("aiFlagged", Sort.Direction.ASC)
                            .on("moderationStatus", Sort.Direction.ASC)
                            .on("createdAt", Sort.Direction.DESC))
                    .block(Duration.ofSeconds(10));
            LOGGER.info("Índices de reseñas verificados");
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "No se pudieron crear los índices de reseñas; se reintentará en el próximo arranque", exception);
        }
    }
}
