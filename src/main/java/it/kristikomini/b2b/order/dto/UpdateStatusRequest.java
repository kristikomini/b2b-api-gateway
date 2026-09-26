package it.kristikomini.b2b.order.dto;

import it.kristikomini.b2b.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull OrderStatus status) {
}
