package it.kristikomini.b2b.order;

/** Thrown when an order does not exist <i>for the caller's tenant</i> → HTTP 404. */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(long id) {
        super("order not found: " + id);
    }
}
