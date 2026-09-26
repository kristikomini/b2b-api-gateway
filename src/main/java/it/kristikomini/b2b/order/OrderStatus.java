package it.kristikomini.b2b.order;

/** Lifecycle of a B2B order. Transitions are enforced in {@code OrderService.updateStatus}. */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    CANCELLED
}
