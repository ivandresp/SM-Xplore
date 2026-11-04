package com.smxplore.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void normalizesEmail() {
        assertThat(new Email("  Ada@Example.COM ").value()).isEqualTo("ada@example.com");
    }

    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(() -> new Email("not-an-email")).isInstanceOf(ValidationException.class);
    }
}
