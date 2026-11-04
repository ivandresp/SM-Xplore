package com.smxplore.securityreports.infrastructure.adapters.in.web.pagination;

import com.smxplore.securityreports.domain.exceptions.InvalidCursorException;
import com.smxplore.securityreports.domain.model.types.Cursor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTests {

    private final CursorCodec codec = new CursorCodec();

    @Test
    void encodesAndDecodesCursor() {
        var cursor = new Cursor(Instant.parse("2026-01-02T10:00:00Z"), UUID.randomUUID());

        var encoded = codec.encode(cursor);

        assertThat(encoded).doesNotContain("|");
        assertThat(codec.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void returnsNullForBlankCursor() {
        assertThat(codec.decode(null)).isNull();
        assertThat(codec.decode("")).isNull();
        assertThat(codec.decode("   ")).isNull();
    }

    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> codec.decode("not-a-valid-cursor"))
                .isInstanceOf(InvalidCursorException.class)
                .hasMessage("Invalid pagination cursor.");
    }
}
