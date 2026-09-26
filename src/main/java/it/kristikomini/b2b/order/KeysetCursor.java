package it.kristikomini.b2b.order;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * The opaque cursor a client passes to fetch the next page: the sort key of the last row it
 * saw, {@code (createdAt, id)}, encoded as URL-safe Base64. Opaque on purpose — the client
 * treats it as a token and cannot fabricate an {@code OFFSET}.
 */
public record KeysetCursor(Instant createdAt, long id) {

    /** {@code <epochMicros>:<id>} Base64-encoded. Micros preserve the DB timestamp precision. */
    public String encode() {
        long micros = createdAt.getEpochSecond() * 1_000_000L + createdAt.getNano() / 1_000L;
        String raw = micros + ":" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static KeysetCursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int sep = raw.indexOf(':');
            long micros = Long.parseLong(raw.substring(0, sep));
            long id = Long.parseLong(raw.substring(sep + 1));
            Instant createdAt = Instant.ofEpochSecond(micros / 1_000_000L, (micros % 1_000_000L) * 1_000L);
            return new KeysetCursor(createdAt, id);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed cursor", e);
        }
    }

    public static KeysetCursor from(Order order) {
        return new KeysetCursor(order.getCreatedAt(), order.getId());
    }
}
