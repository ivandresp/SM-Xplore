package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityFilter;
import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.ports.repository.ActivityRepositoryPort;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.ActivityEntity;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.mapper.ActivityPersistenceMapper;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.repository.ActivityR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ActivityPostgresAdapter implements ActivityRepositoryPort {

    private final ActivityR2dbcRepository repository;
    private final R2dbcEntityTemplate template;
    private final ActivityPersistenceMapper mapper;

    /**
     * R2DBC has no upsert, so an explicit existence check decides between insert and update.
     * shortcut: no optimistic locking; add a @Version column when concurrent edits matter.
     *
     * <p>{@code updated_at} se reescribe en cada escritura porque es la marca del feed de
     * exportación del catálogo: sin esto una actividad nunca aparecería en el delta incremental.
     */
    @Override
    public Mono<Activity> save(Activity activity) {
        var entity = mapper.toEntity(activity.toBuilder().updatedAt(Instant.now()).build());
        return repository.existsById(entity.id())
                .flatMap(exists -> exists ? template.update(entity) : template.insert(entity))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Activity> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Mono<Activity> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Flux<Activity> findAll() {
        return repository.findAll().map(mapper::toDomain);
    }

    @Override
    public Flux<Activity> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Flux.empty();
        }
        return repository.findAllById(ids).map(mapper::toDomain);
    }

    @Override
    public Flux<Activity> findAllByGuideId(UUID guideId) {
        return repository.findAllByGuideId(guideId).map(mapper::toDomain);
    }

    @Override
    public Mono<PageResult<Activity>> findPage(ActivityFilter filter) {
        var conditions = new StringBuilder(" FROM actividad WHERE 1 = 1");
        if (filter.status() != null) {
            conditions.append(" AND status = :status");
        }
        if (hasText(filter.search())) {
            conditions.append(" AND translate(lower(name), 'áéíóúüñ', 'aeiouun')"
                    + " LIKE '%' || translate(lower(:search), 'áéíóúüñ', 'aeiouun') || '%'");
        }
        if (hasText(filter.category())) {
            conditions.append(" AND lower(category) = lower(:category)");
        }
        if (filter.siteId() != null) {
            conditions.append(" AND sitio_id = :siteId");
        }
        if (filter.guideId() != null) {
            conditions.append(" AND guia_id = :guideId");
        }
        if (filter.priceMin() != null) {
            conditions.append(" AND price >= :priceMin");
        }
        if (filter.priceMax() != null) {
            conditions.append(" AND price <= :priceMax");
        }
        if (filter.startsAfter() != null) {
            conditions.append(" AND starts_at >= :startsAfter");
        }
        if (filter.startsBefore() != null) {
            conditions.append(" AND starts_at <= :startsBefore");
        }

        var client = template.getDatabaseClient();
        var offset = (long) filter.page() * filter.size();
        var rows = bind(client.sql("SELECT *" + conditions + " ORDER BY " + orderBy(filter.sort())
                + " LIMIT :limit OFFSET :offset"), filter)
                .bind("limit", filter.size())
                .bind("offset", offset)
                .map((row, metadata) -> template.getConverter().read(ActivityEntity.class, row))
                .all()
                .map(mapper::toDomain)
                .collectList();
        var total = bind(client.sql("SELECT COUNT(*)" + conditions), filter)
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .defaultIfEmpty(0L);

        return Mono.zip(rows, total)
                .map(result -> new PageResult<>(result.getT1(), filter.page(), filter.size(), result.getT2()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Orden por lista blanca: solo columnas conocidas, nunca texto del cliente. */
    private static String orderBy(String sort) {
        return switch (sort == null ? "" : sort) {
            case "priceAsc" -> "price, id";
            case "priceDesc" -> "price DESC, id";
            case "dateAsc" -> "starts_at, id";
            case "dateDesc" -> "starts_at DESC, id";
            default -> "name, id";
        };
    }

    /** Bindea solo los filtros presentes: R2DBC no puede inferir el tipo de un parámetro nulo. */
    private static DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec,
                                                         ActivityFilter filter) {
        if (filter.status() != null) {
            spec = spec.bind("status", filter.status().name());
        }
        if (hasText(filter.search())) {
            spec = spec.bind("search", filter.search().trim());
        }
        if (hasText(filter.category())) {
            spec = spec.bind("category", filter.category().trim());
        }
        if (filter.siteId() != null) {
            spec = spec.bind("siteId", filter.siteId());
        }
        if (filter.guideId() != null) {
            spec = spec.bind("guideId", filter.guideId());
        }
        if (filter.priceMin() != null) {
            spec = spec.bind("priceMin", filter.priceMin());
        }
        if (filter.priceMax() != null) {
            spec = spec.bind("priceMax", filter.priceMax());
        }
        if (filter.startsAfter() != null) {
            spec = spec.bind("startsAfter", filter.startsAfter());
        }
        if (filter.startsBefore() != null) {
            spec = spec.bind("startsBefore", filter.startsBefore());
        }
        return spec;
    }

    @Override
    public Flux<Activity> findAllByCategory(String category) {
        return repository.findAllByCategoryIgnoreCase(category).map(mapper::toDomain);
    }

    @Override
    public Mono<PageResult<Activity>> findPublishedByCategoryPage(String category, int page, int size) {
        var offset = (long) page * size;
        return Mono.zip(
                        repository.findPagePublishedByCategory(category, size, offset)
                                .map(mapper::toDomain)
                                .collectList(),
                        repository.countPublishedByCategory(category).defaultIfEmpty(0L))
                .map(result -> new PageResult<>(result.getT1(), page, size, result.getT2()));
    }

    @Override
    public Flux<Activity> findAllBySiteId(UUID siteId) {
        return repository.findAllBySiteId(siteId).map(mapper::toDomain);
    }

    @Override
    public Mono<PageResult<Activity>> findPublishedBySiteIdPage(UUID siteId, int page, int size) {
        var offset = (long) page * size;
        return Mono.zip(
                        repository.findPagePublishedBySiteId(siteId, size, offset)
                                .map(mapper::toDomain)
                                .collectList(),
                        repository.countPublishedBySiteId(siteId).defaultIfEmpty(0L))
                .map(result -> new PageResult<>(result.getT1(), page, size, result.getT2()));
    }

    @Override
    public Flux<UUID> findIdsBySiteIds(Collection<UUID> siteIds) {
        return repository.findIdsBySiteIds(siteIds);
    }

    /**
     * Dos variantes en lugar de un {@code IS NULL OR} con parámetro nulo: R2DBC no puede inferir el
     * tipo del bind nulo y PostgreSQL rechaza la consulta.
     */
    @Override
    public Mono<PageResult<Activity>> findUpdatedSince(Instant updatedSince, int page, int size) {
        var offset = (long) page * size;
        var content = updatedSince == null
                ? repository.findPageAllForExport(size, offset)
                : repository.findPageUpdatedSince(updatedSince, size, offset);
        var total = updatedSince == null
                ? repository.countAllForExport()
                : repository.countUpdatedSince(updatedSince);
        return Mono.zip(content.map(mapper::toDomain).collectList(), total.defaultIfEmpty(0L))
                .map(result -> new PageResult<>(result.getT1(), page, size, result.getT2()));
    }
}
