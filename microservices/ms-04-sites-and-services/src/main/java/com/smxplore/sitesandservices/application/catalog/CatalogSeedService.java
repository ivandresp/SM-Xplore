package com.smxplore.sitesandservices.application.catalog;

import com.smxplore.sitesandservices.domain.model.catalog.Category;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryFilter;
import com.smxplore.sitesandservices.domain.model.catalog.CategoryScope;
import com.smxplore.sitesandservices.domain.ports.out.CategoryRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Catalogo semilla base (P3-10, opcion A firmada por el PL).
 *
 * <p>Si {@code app.catalog.seed-enabled=true}, al arrancar crea las categorias base que falten.
 * Es idempotente: compara por nombre sin distinguir mayusculas y nunca duplica una categoria
 * existente. No borra ni edita nada; un administrador puede renombrar o crear mas por la API.
 *
 * <p>La lista es deliberadamente corta y de alcance {@link CategoryScope#GENERAL}: sirve para que
 * una instalacion nueva tenga taxonomia minima para sitios, servicios, actividades y eventos. Las
 * categorias hijas (con {@code parentId}) y las de alcance especifico las crea el administrador.
 */
@Service
@RequiredArgsConstructor
public class CatalogSeedService {

    static final int PAGE_SIZE = 100;

    static final List<String> BASE_CATEGORIES = List.of(
            "AVENTURA",
            "CULTURA",
            "GASTRONOMIA",
            "NATURALEZA",
            "BIENESTAR",
            "RECREACION");

    private final CategoryRepositoryPort categoryRepository;
    private final Clock clock;

    /**
     * Crea las categorias base que no existan.
     *
     * @return cuantas categorias se crearon (0 si el catalogo ya estaba completo)
     */
    public Mono<Integer> seed() {
        Instant now = Instant.now(clock);
        return existingCategoryNames()
                .flatMap(existing -> Flux.fromIterable(BASE_CATEGORIES)
                        .filter(name -> !existing.contains(name))
                        .concatMap(name -> categoryRepository.save(newCategory(name, now)))
                        .count()
                        .map(Long::intValue));
    }

    private Mono<List<String>> existingCategoryNames() {
        return collectPage(0, new ArrayList<>());
    }

    private Mono<List<String>> collectPage(int page, List<String> names) {
        CategoryFilter filter = new CategoryFilter(CategoryScope.GENERAL, null, null, page, PAGE_SIZE);
        return categoryRepository.findAll(filter)
                .flatMap(result -> {
                    result.content().forEach(category -> names.add(normalize(category.name())));
                    boolean hasNext = (long) (page + 1) * PAGE_SIZE < result.total();
                    return hasNext ? collectPage(page + 1, names) : Mono.just(names);
                });
    }

    private Category newCategory(String name, Instant now) {
        Category category = Category.builder()
                .categoryId(UUID.randomUUID())
                .name(name)
                .scope(CategoryScope.GENERAL)
                .updatedAt(now)
                .build();
        category.validate();
        return category;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }
}
