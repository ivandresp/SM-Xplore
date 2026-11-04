package com.smxplore.securityreports.infrastructure.adapters.out.persistence.adapter;

import com.smxplore.securityreports.domain.model.types.Cursor;
import com.smxplore.securityreports.domain.model.types.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Shared cursor pagination operations for the MongoDB persistence adapters.
 */
public record CursorPaginationSupport() {

    static <T> Page<T> toPage(List<T> items, int size, Function<T, Cursor> cursorExtractor) {
        if (items.size() <= size) {
            return new Page<>(items, null);
        }

        var content = new ArrayList<>(items.subList(0, size));
        return new Page<>(content, cursorExtractor.apply(content.getLast()));
    }

    static void applyCursor(Query query, Cursor cursor, Sort.Direction direction) {
        if (cursor == null) return;

        var id = cursor.id().toString();
        var dateCriterion = direction == Sort.Direction.DESC
                ? Criteria.where("createdAt").lt(cursor.createdAt())
                : Criteria.where("createdAt").gt(cursor.createdAt());
        var idCriterion = direction == Sort.Direction.DESC
                ? Criteria.where("_id").lt(id)
                : Criteria.where("_id").gt(id);

        query.addCriteria(new Criteria().orOperator(
                dateCriterion,
                new Criteria().andOperator(
                        Criteria.where("createdAt").is(cursor.createdAt()),
                        idCriterion
                )
        ));
    }
}
