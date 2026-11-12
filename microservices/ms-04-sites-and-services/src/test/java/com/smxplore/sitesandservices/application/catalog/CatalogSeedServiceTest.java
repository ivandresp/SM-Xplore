package com.smxplore.sitesandservices.application.catalog;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryFilter;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.event.DomainEvent;
import com.smxplore.sitesandservices.domain.ports.out.CategoryRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.OutboxPort;
import com.smxplore.sitesandservices.domain.ports.out.TransactionPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSeedServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final RecordingCategoryRepository repository = new RecordingCategoryRepository();
    private final List<DomainEvent> published = new ArrayList<>();
    private final TransactionPort passthrough = new TransactionPort() {
        @Override
        public <T> Mono<T> execute(Mono<T> work) {
            return work;
        }
    };
    private final OutboxPort outbox = event -> {
        published.add(event);
        return Mono.empty();
    };
    private final TransactionalOutboxWriter outboxWriter = new TransactionalOutboxWriter(passthrough, outbox);
    private final DomainEventFactory eventFactory = new DomainEventFactory("ms-04-sites-and-services", CLOCK);

    @Test
    void createsEveryBaseCategoryWhenTheCatalogIsEmpty() {
        CatalogSeedService service = service();

        StepVerifier.create(service.seed())
                .expectNext(CatalogSeedService.BASE_CATEGORIES.size())
                .verifyComplete();

        assertThat(repository.saved).extracting(Category::name)
                .containsExactlyElementsOf(CatalogSeedService.BASE_CATEGORIES);
        assertThat(repository.saved).allSatisfy(category -> {
            assertThat(category.categoryId()).isNotNull();
            assertThat(category.scope()).isEqualTo(CategoryScope.GENERAL);
            assertThat(category.updatedAt()).isEqualTo(NOW);
        });
        // La semilla tambien publica en el outbox para que el grafo (ms-05) proyecte las categorias.
        assertThat(published).extracting(DomainEvent::eventType).containsOnly("catalog.category.changed");
        assertThat(published).extracting(DomainEvent::correlationId).containsOnly("catalog-seed");
    }

    @Test
    void skipsCategoriesThatAlreadyExistIgnoringCase() {
        repository.existing.add(Category.builder()
                .categoryId(UUID.randomUUID())
                .name("aventura")
                .scope(CategoryScope.GENERAL)
                .updatedAt(NOW)
                .build());
        CatalogSeedService service = service();

        StepVerifier.create(service.seed())
                .expectNext(CatalogSeedService.BASE_CATEGORIES.size() - 1)
                .verifyComplete();

        assertThat(repository.saved).extracting(Category::name)
                .doesNotContain("AVENTURA")
                .contains("CULTURA");
    }

    @Test
    void readsEveryPageBeforeDecidingWhatIsMissing() {
        for (int index = 0; index < 150; index++) {
            repository.existing.add(Category.builder()
                    .categoryId(UUID.randomUUID())
                    .name(String.format("EXISTENTE-%03d", index))
                    .scope(CategoryScope.GENERAL)
                    .updatedAt(NOW)
                    .build());
        }
        CatalogSeedService service = service();

        StepVerifier.create(service.seed())
                .expectNext(CatalogSeedService.BASE_CATEGORIES.size())
                .verifyComplete();

        // 150 categorias con tamano de pagina 100 obligan a leer dos paginas.
        assertThat(repository.requestedPages).containsExactly(0, 1);
    }

    private CatalogSeedService service() {
        return new CatalogSeedService(repository, outboxWriter, eventFactory, CLOCK);
    }

    private static final class RecordingCategoryRepository implements CategoryRepositoryPort {

        private final List<Category> existing = new ArrayList<>();
        private final List<Category> saved = new ArrayList<>();
        private final List<Integer> requestedPages = new ArrayList<>();

        @Override
        public Mono<Category> save(Category category) {
            saved.add(category);
            existing.add(category);
            return Mono.just(category);
        }

        @Override
        public Mono<Category> findById(UUID categoryId) {
            return Mono.empty();
        }

        @Override
        public Mono<PageResult<Category>> findAll(CategoryFilter filter) {
            requestedPages.add(filter.page());
            int from = Math.min(filter.page() * filter.size(), existing.size());
            int to = Math.min(from + filter.size(), existing.size());
            return Mono.just(new PageResult<>(
                    List.copyOf(existing.subList(from, to)), filter.page(), filter.size(), existing.size()));
        }

        @Override
        public Mono<PageResult<Category>> findUpdatedSince(Instant updatedSince, int page, int size) {
            return Mono.empty();
        }
    }
}
