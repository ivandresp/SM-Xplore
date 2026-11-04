package com.smxplore.securityreports.infrastructure.adapters.in.web.pagination;

import com.smxplore.securityreports.domain.exceptions.InvalidCursorException;
import com.smxplore.securityreports.domain.model.types.Cursor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Encodes and decodes the cursor as a URL-safe opaque value. The frontend
 * should treat it as a black box and only send it back unchanged.
 */
@Component
public class CursorCodec {

    private static final String SEPARATOR = "|";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public String encode(Cursor cursor) {
        if (cursor == null) return null;

        var raw = cursor.createdAt().toEpochMilli() + SEPARATOR + cursor.id();
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;

        try {
            var raw = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            var parts = raw.split("\\" + SEPARATOR, 2);
            if (parts.length != 2) throw new InvalidCursorException("Invalid pagination cursor.");

            return new Cursor(
                    Instant.ofEpochMilli(Long.parseLong(parts[0])),
                    UUID.fromString(parts[1])
            );
        } catch (InvalidCursorException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidCursorException("Invalid pagination cursor.", exception);
        }
    }
}
