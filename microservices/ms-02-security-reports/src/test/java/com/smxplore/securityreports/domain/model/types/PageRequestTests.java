package com.smxplore.securityreports.domain.model.types;

import com.smxplore.securityreports.domain.exceptions.InvalidPageSizeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestTests {

    @Test
    void createsFirstPageRequest() {
        var pageRequest = PageRequest.first(PageRequest.DEFAULT_SIZE);

        assertThat(pageRequest.size()).isEqualTo(PageRequest.DEFAULT_SIZE);
        assertThat(pageRequest.cursor()).isNull();
        assertThat(pageRequest.isFirstPage()).isTrue();
    }

    @Test
    void rejectsSizeBelowMinimum() {
        assertThatThrownBy(() -> PageRequest.first(0))
                .isInstanceOf(InvalidPageSizeException.class)
                .hasMessage("Page size must be between 1 and 100.");
    }

    @Test
    void rejectsSizeAboveMaximum() {
        assertThatThrownBy(() -> PageRequest.first(PageRequest.MAX_SIZE + 1))
                .isInstanceOf(InvalidPageSizeException.class)
                .hasMessage("Page size must be between 1 and 100.");
    }
}
