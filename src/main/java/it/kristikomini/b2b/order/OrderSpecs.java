package it.kristikomini.b2b.order;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Composable {@link Specification} building blocks for the order search. Each filter is one
 * predicate; the service {@code and()}s together only the ones actually supplied. This is the
 * alternative to string-concatenated SQL (injection-prone) and to a {@code @Query} per filter
 * combination (combinatorial explosion).
 */
public final class OrderSpecs {

    /** Always applied: a caller only ever sees their own tenant's rows. */
    public static Specification<Order> forTenant(String tenant) {
        return (root, query, cb) -> cb.equal(root.get("tenant"), tenant);
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> createdAfter(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> amountAtLeast(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), min);
    }

    public static Specification<Order> descriptionContains(String text) {
        String like = "%" + text.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), like);
    }

    /**
     * Keyset predicate: rows strictly <i>after</i> the cursor in {@code (createdAt, id)}
     * descending order. Expressed as {@code createdAt < c OR (createdAt = c AND id < i)} — the
     * id tiebreak is what gives a strict total order so no row is skipped or repeated.
     */
    public static Specification<Order> keysetBefore(Instant lastCreatedAt, long lastId) {
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("createdAt"), lastCreatedAt),
                cb.and(
                        cb.equal(root.get("createdAt"), lastCreatedAt),
                        cb.lessThan(root.get("id"), lastId)));
    }

    private OrderSpecs() {
    }
}
