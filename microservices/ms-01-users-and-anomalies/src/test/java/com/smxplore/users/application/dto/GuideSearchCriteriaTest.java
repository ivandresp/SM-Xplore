package com.smxplore.users.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.RegistrationStatus;
import org.junit.jupiter.api.Test;

class GuideSearchCriteriaTest {

    @Test
    void defaultsToPendingGuides() {
        GuideSearchCriteria criteria = new GuideSearchCriteria(null, 0, 20);

        assertThat(criteria.registrationStatus()).isEqualTo(RegistrationStatus.PENDIENTE);
        assertThat(criteria.page()).isZero();
        assertThat(criteria.size()).isEqualTo(20);
    }

    @Test
    void keepsTheRequestedStatus() {
        GuideSearchCriteria criteria = new GuideSearchCriteria(RegistrationStatus.APROBADO, 2, 10);

        assertThat(criteria.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
        assertThat(criteria.page()).isEqualTo(2);
        assertThat(criteria.size()).isEqualTo(10);
    }

    @Test
    void rejectsAnOutOfRangePageSize() {
        assertThatThrownBy(() -> new GuideSearchCriteria(null, 0, 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> new GuideSearchCriteria(null, 0, GuideSearchCriteria.MAX_SIZE + 1))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsANegativePage() {
        assertThatThrownBy(() -> new GuideSearchCriteria(null, -1, 20)).isInstanceOf(ValidationException.class);
    }
}
