package com.smxplore.users.identity.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.identity.domain.exception.ValidationException;
import com.smxplore.users.identity.domain.model.Role;
import org.junit.jupiter.api.Test;

class UserSearchCriteriaTest {

    @Test
    void normalizesBlankQueryAndDefaultSorting() {
        UserSearchCriteria criteria = new UserSearchCriteria("  ", null, null, null, null, null, 0, 20);

        assertThat(criteria.q()).isNull();
        assertThat(criteria.sortBy()).isEqualTo(UserSearchCriteria.DEFAULT_SORT);
        assertThat(criteria.sortOrder()).isEqualTo(SortOrder.DESC);
    }

    @Test
    void keepsTheRequestedFilters() {
        UserSearchCriteria criteria =
                new UserSearchCriteria(" 1234 ", Role.GUIA, null, null, "fullName", SortOrder.ASC, 2, 10);

        assertThat(criteria.q()).isEqualTo("1234");
        assertThat(criteria.role()).isEqualTo(Role.GUIA);
        assertThat(criteria.page()).isEqualTo(2);
        assertThat(criteria.size()).isEqualTo(10);
    }

    @Test
    void rejectsAnUnknownSortField() {
        assertThatThrownBy(() -> new UserSearchCriteria(null, null, null, null, "password", null, 0, 20))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ordenamiento");
    }

    @Test
    void rejectsAnOutOfRangePageSize() {
        assertThatThrownBy(() -> new UserSearchCriteria(null, null, null, null, null, null, 0, 0))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() ->
                        new UserSearchCriteria(null, null, null, null, null, null, 0, UserSearchCriteria.MAX_SIZE + 1))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsANegativePage() {
        assertThatThrownBy(() -> new UserSearchCriteria(null, null, null, null, null, null, -1, 20))
                .isInstanceOf(ValidationException.class);
    }
}
