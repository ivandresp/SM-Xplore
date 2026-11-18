package com.smxplore.sitesandservices.application.catalog;

import com.smxplore.sitesandservices.application.event.DomainEventFactory;
import com.smxplore.sitesandservices.application.support.CatalogAccess;
import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.application.support.TransactionalOutboxWriter;
import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.ports.out.CategoryRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.ServiceRepositoryPort;
import com.smxplore.sitesandservices.domain.ports.out.SiteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Crear categorias permite ADMIN y DESTINATION_MANAGER; editar sigue siendo solo ADMIN. El arbol no admite
 * ciclos ni padres inexistentes. Estos tests no necesitan Docker.
 */
class CategoryTaxonomyServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final UUID ADMIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222201");
    private static final UUID MANAGER_ID = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID PARENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333301");
    private static final UUID CHILD_ID = UUID.fromString("44444444-4444-4444-4444-444444444401");

    private final CatalogAccess access = new CatalogAccess();
    private final Actor admin = new Actor(ADMIN_ID, UserRole.ADMIN, "Admin");
    private final Actor manager = new Actor(MANAGER_ID, UserRole.DESTINATION_MANAGER, "Gestora");

    private CategoryRepositoryPort categoryRepository;
    private SiteRepositoryPort siteRepository;
    private ServiceRepositoryPort serviceRepository;
    private CatalogLookup lookup;
    private TransactionalOutboxWriter outboxWriter;
    private CategoryCreateService createService;
    private CategoryUpdateService updateService;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepositoryPort.class);
        siteRepository = mock(SiteRepositoryPort.class);
        serviceRepository = mock(ServiceRepositoryPort.class);
        lookup = mock(CatalogLookup.class);
        outboxWriter = mock(TransactionalOutboxWriter.class);
        DomainEventFactory eventFactory = mock(DomainEventFactory.class);

        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(outboxWriter.save(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, Mono.class));
        when(siteRepository.updateCategorySnapshot(any(), any())).thenReturn(Mono.empty());
        when(serviceRepository.updateCategorySnapshot(any(), any())).thenReturn(Mono.empty());

        createService = new CategoryCreateService(
                categoryRepository, siteRepository, serviceRepository, lookup, access,
                outboxWriter, eventFactory, CLOCK);
        updateService = new CategoryUpdateService(
                categoryRepository, siteRepository, serviceRepository, lookup, access,
                outboxWriter, eventFactory, CLOCK);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"GUIDE", "SERVICE_PROVIDER", "TOURIST"})
    void unauthorizedRolesCannotCreateBeforeAnyIo(UserRole role) {
        Category request = Category.builder()
                .name("Naturaleza")
                .scope(CategoryScope.GENERAL)
                .parentId(PARENT_ID)
                .build();

        StepVerifier.create(createService.handle(request, new Actor(MANAGER_ID, role, "User"), "corr-1"))
                .expectError(ForbiddenOperationException.class)
                .verify();

        verifyNoInteractions(categoryRepository, siteRepository, serviceRepository, lookup, outboxWriter);
    }

    @ParameterizedTest
    @CsvSource({
            "ADMIN,GENERAL", "ADMIN,SITE", "ADMIN,EVENT", "ADMIN,ACTIVITY", "ADMIN,SERVICE",
            "DESTINATION_MANAGER,GENERAL", "DESTINATION_MANAGER,SITE", "DESTINATION_MANAGER,EVENT",
            "DESTINATION_MANAGER,ACTIVITY", "DESTINATION_MANAGER,SERVICE"
    })
    void authorizedRolesCreateCategoriesInEveryScope(UserRole role, CategoryScope scope) {
        Category request = Category.builder().name("Naturaleza").scope(scope).build();
        Actor actor = new Actor(ADMIN_ID, role, "User");

        Category created = createService.handle(request, actor, "corr-2").block();

        assertThat(created).isNotNull();
        assertThat(created.categoryId()).isNotNull();
        assertThat(created.name()).isEqualTo("Naturaleza");
        assertThat(created.scope()).isEqualTo(scope);
        assertThat(created.updatedAt()).isEqualTo(CLOCK.instant());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void rejectsCategoriesWithUnknownParent() {
        when(lookup.category(PARENT_ID))
                .thenReturn(Mono.error(new ResourceNotFoundException("Category", PARENT_ID)));
        Category request = Category.builder()
                .name("Playas")
                .scope(CategoryScope.GENERAL)
                .parentId(PARENT_ID)
                .build();

        StepVerifier.create(createService.handle(request, admin, "corr-3"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidCatalogException.class)
                        .hasMessageContaining("parent category does not exist"))
                .verify();
    }

    @Test
    void managerCannotUpdateCategories() {
        Category request = Category.builder()
                .categoryId(CHILD_ID)
                .name("Playas")
                .scope(CategoryScope.GENERAL)
                .parentId(PARENT_ID)
                .build();

        StepVerifier.create(updateService.handle(request, manager, "corr-4"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ForbiddenOperationException.class)
                        .hasMessageContaining("global category taxonomy"))
                .verify();

        verifyNoInteractions(lookup);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    void rejectsCyclesWhenUpdatingTheHierarchy() {
        Category parent = category(PARENT_ID, "Naturaleza", CHILD_ID);
        Category child = category(CHILD_ID, "Playas", PARENT_ID);
        when(lookup.category(PARENT_ID)).thenReturn(Mono.just(parent));
        when(lookup.category(CHILD_ID)).thenReturn(Mono.just(child));
        Category request = Category.builder()
                .categoryId(PARENT_ID)
                .name("Naturaleza")
                .scope(CategoryScope.GENERAL)
                .parentId(CHILD_ID)
                .build();

        StepVerifier.create(updateService.handle(request, admin, "corr-5"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidCatalogException.class)
                        .hasMessageContaining("cycle"))
                .verify();
    }

    @Test
    void updatesAChildCategoryWhenTheParentChainIsValid() {
        Category parent = category(PARENT_ID, "Naturaleza", null);
        Category child = category(CHILD_ID, "Playas", PARENT_ID);
        when(lookup.category(PARENT_ID)).thenReturn(Mono.just(parent));
        when(lookup.category(CHILD_ID)).thenReturn(Mono.just(child));
        Category request = Category.builder()
                .categoryId(CHILD_ID)
                .name("Playas y costas")
                .scope(CategoryScope.GENERAL)
                .parentId(PARENT_ID)
                .build();

        Category updated = updateService.handle(request, admin, "corr-6").block();

        assertThat(updated).isNotNull();
        assertThat(updated.categoryId()).isEqualTo(CHILD_ID);
        assertThat(updated.name()).isEqualTo("Playas y costas");
        assertThat(updated.updatedAt()).isEqualTo(CLOCK.instant());
        verify(categoryRepository).save(any(Category.class));
    }

    private static Category category(UUID categoryId, String name, UUID parentId) {
        return Category.builder()
                .categoryId(categoryId)
                .name(name)
                .scope(CategoryScope.GENERAL)
                .parentId(parentId)
                .updatedAt(CLOCK.instant())
                .build();
    }
}
