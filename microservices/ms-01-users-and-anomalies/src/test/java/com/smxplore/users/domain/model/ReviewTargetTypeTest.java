package com.smxplore.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ReviewTargetTypeTest {

    @Test
    void exposesEnglishCodes() {
        assertThat(ReviewTargetType.GUIDE.code()).isEqualTo("GUIDE");
        assertThat(ReviewTargetType.PROVIDER.code()).isEqualTo("SERVICE_PROVIDER");
    }

    @Test
    void acceptsLegacySpanishCodes() {
        assertThat(ReviewTargetType.GUIDE.codes()).containsExactly("GUIDE", "GUIA");
        assertThat(ReviewTargetType.PROVIDER.codes()).containsExactly("SERVICE_PROVIDER", "PRESTADORA_SERVICIOS");
        assertThat(ReviewTargetType.fromCode("GUIA")).isEqualTo(ReviewTargetType.GUIDE);
        assertThat(ReviewTargetType.fromCode("PRESTADORA_SERVICIOS")).isEqualTo(ReviewTargetType.PROVIDER);
        assertThat(ReviewTargetType.fromCode("GUIDE")).isEqualTo(ReviewTargetType.GUIDE);
        assertThat(ReviewTargetType.fromCode("SERVICE_PROVIDER")).isEqualTo(ReviewTargetType.PROVIDER);
    }

    @Test
    void rejectsUnknownCodes() {
        assertThatThrownBy(() -> ReviewTargetType.fromCode("SITE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desconocido");
    }
}
