package it.kristikomini.b2b.order.dto;

import it.kristikomini.b2b.order.Order;
import it.kristikomini.b2b.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API representation of an order. Also the value cached by the idempotency aspect, so it is a
 * plain serializable record with no entity/JPA coupling.
 */
public record OrderResponse(
        Long id,
        String tenant,
        String customerRef,
        OrderStatus status,
        BigDecimal amount,
        String description,
        Instant createdAt) {

    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getTenant(), o.getCustomerRef(),
                o.getStatus(), o.getAmount(), o.getDescription(), o.getCreatedAt());
    }
}
