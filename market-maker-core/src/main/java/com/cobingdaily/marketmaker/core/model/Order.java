package com.cobingdaily.marketmaker.core.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Order(
        String orderId,
        OrderType type,
        Side side,
        BigDecimal price,
        BigDecimal quantity,
        String traderId,
        Instant timestamp
) {
    public Order {
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        Objects.requireNonNull(type, "Order type cannot be null");
        Objects.requireNonNull(side, "Side cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(traderId, "Trader ID cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
