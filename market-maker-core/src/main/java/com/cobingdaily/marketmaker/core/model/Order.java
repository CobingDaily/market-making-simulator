package com.cobingdaily.marketmaker.core.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a trading order in the market making system.
 *
 * <p>This is an immutable value object that captures all the essential
 * information about a trading order. Orders are uniquely identified by
 * their orderId and maintain price-time priority in the order book.
 *
 * <p>Key constraints:
 * <ul>
 *   <li>All fields are required except price (which is only required for LIMIT orders)</li>
 *   <li>Quantity must be positive</li>
 *   <li>Price must be positive for LIMIT orders</li>
 *   <li>Order IDs must be unique across the system</li>
 * </ul>
 *
 * @param orderId   Unique identifier for the order
 * @param type      Order type (MARKET or LIMIT)
 * @param side      Side of the order (BUY or SELL)
 * @param price     Price per unit (required for LIMIT orders, optional for MARKET)
 * @param quantity  Number of units to trade
 * @param traderId  Identifier of the trader placing the order
 * @param timestamp When the order was created
 *
 * @see OrderType
 * @see Side
 */
public record Order(
        String orderId,
        OrderType type,
        Side side,
        BigDecimal price,
        BigDecimal quantity,
        String traderId,
        Instant timestamp
) {
    /** Price scale for consistent decimal handling */
    private static final int PRICE_SCALE = 2;
    private static final int QUANTITY_SCALE = 0;

    /**
     * Compact constructor with comprehensive validation.
     */
    public Order {
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        Objects.requireNonNull(type, "Order type cannot be null");
        Objects.requireNonNull(side, "Side cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(traderId, "Trader ID cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");

        if (orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID cannot be blank");
        }

        if (traderId.isBlank()) {
            throw new IllegalArgumentException("Trader ID cannot be blank");
        }

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (type == OrderType.LIMIT) {
            Objects.requireNonNull(price, "Price is required for LIMIT orders");
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Price must be positive for limit order, but was: " + price);
            }
        }

        // Normalize decimal scales for consistent comparisons
        if (price != null) {
            price = price.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        }
        quantity = quantity.setScale(QUANTITY_SCALE, RoundingMode.DOWN);
    }

    /**
     * Calculates the total value of this order.
     *
     * @return price * quantity, or zero for market orders
     */
    public BigDecimal getValue() {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(quantity);
    }

    /**
     * Checks if this order can match with another order.
     * Orders can match if they are on opposite sides and prices cross.
     *
     * @param other the order to check against
     * @return true if orders can potentially match
     */
    public boolean canMatch(Order other) {
        Objects.requireNonNull(other, "Other order cannot be null");

        if (side == other.side) {
            return false;
        }

        // Market orders can always match
        if (type == OrderType.MARKET || other.type == OrderType.MARKET) {
            return true;
        }

        // For limit orders check if prices cross
        return pricesCross(other);
    }

    /**
     * Prices cross if:
     * <ul>
     *   <li>For a BUY order, the price is greater than or equal to the SELL order's price</li>
     *   <li>For a SELL order, the price is less than or equal to the BUY order's price</li>
     * </ul>
     */
    private boolean pricesCross(Order other) {
        return switch (side) {
            case BUY -> price.compareTo(other.price) >= 0;
            case SELL -> price.compareTo(other.price) <= 0;
        };
    }
}
