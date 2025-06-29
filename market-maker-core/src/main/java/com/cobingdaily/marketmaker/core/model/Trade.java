package com.cobingdaily.marketmaker.core.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record Trade(
        String buyer,
        String seller,
        BigDecimal price,
        BigDecimal quantity,
        Instant timestamp
) {
    public Trade {
        Objects.requireNonNull(buyer, "Buyer cannot be null");
        Objects.requireNonNull(seller, "Order type cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
