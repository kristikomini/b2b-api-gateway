package it.kristikomini.b2b.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank String customerRef,
        @NotNull @Positive BigDecimal amount,
        String description) {
}
