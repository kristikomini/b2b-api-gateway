package it.kristikomini.b2b.order;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The cursor is the whole contract of keyset pagination — it must round-trip exactly. */
class KeysetCursorTest {

    @Test
    void encodesAndDecodesBackToTheSameKey() {
        KeysetCursor original = new KeysetCursor(Instant.parse("2026-01-15T10:20:30.123456Z"), 4242L);

        KeysetCursor roundTripped = KeysetCursor.decode(original.encode());

        assertThat(roundTripped.id()).isEqualTo(4242L);
        assertThat(roundTripped.createdAt()).isEqualTo(original.createdAt()); // microsecond precision preserved
    }

    @Test
    void producesUrlSafeOpaqueToken() {
        String token = new KeysetCursor(Instant.parse("2026-01-15T10:20:30Z"), 1L).encode();

        // URL-safe Base64: no '+', '/', or '=' that would need escaping in a query string.
        assertThat(token).doesNotContain("+", "/", "=");
    }

    @Test
    void rejectsAMalformedCursor() {
        assertThatThrownBy(() -> KeysetCursor.decode("!!!not valid!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("malformed cursor");
    }
}
