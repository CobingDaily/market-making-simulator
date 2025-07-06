package com.cobingdaily.marketmaker.engine;

import com.cobingdaily.marketmaker.core.model.Order;
import com.cobingdaily.marketmaker.core.model.OrderType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Validates orders before they are processed by the matching engine.
 *
 * <p>This class ensures that orders meet all business rules and constraints
 * before being added to the order book or matched against existing orders.
 *
 * <p>Validation rules include:
 * <ul>
 *   <li>Basic field validation (handled by Order constructor)</li>
 *   <li>Self-trading prevention</li>
 *   <li>Price reasonability checks</li>
 *   <li>Quantity limits</li>
 * </ul>
 */
public class OrderValidator {

    /** 1 million for the maximal price. */
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000.00");
    /** 1 cent for the minimal price. */
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    /** 1 million for the maximal quantity. */
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("1000000.00");
    /** 1/100 for the minimal quantity. */
    private static final BigDecimal MIN_QUANTITY = new BigDecimal("0.01");

    /**
     * Validates an incoming order.
     *
     * <p>Note: Basic validation (null checks, positive values) is already
     * handled by the Order constructor. This method performs additional
     * business rule validation.
     *
     * @param order the order to validate
     * @throws ValidationException if the order violates any business rules
     * @throws NullPointerException if the order is null
     */
    public void validateOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");

        validateQuantity(order.quantity());

        if (order.type() == OrderType.LIMIT) {
            validatePrice(order.price());
        }
    }

    /**
     * Validates an order against another to prevent self-trading.
     *
     * <p>Self-trading occurs when the same trader would be on both sides
     * of a trade. This is typically prohibited in real markets.
     *
     * @param incomingOrder the incoming order
     * @param existingOrder the existing order in the book
     * @return true if the trade is allowed, false if it would be self-trading
     */
    public boolean canTrade(Order incomingOrder, Order existingOrder) {
        Objects.requireNonNull(incomingOrder, "Incoming order cannot be null");
        Objects.requireNonNull(existingOrder, "Existing order cannot be null");

        return !incomingOrder.traderId().equals(existingOrder.traderId());
    }

    /**
     * Validates that a price is within acceptable bounds.
     *
     * @param price the price to validate
     * @throws ValidationException if the price is out of bounds
     */
    private void validatePrice(BigDecimal price) {
        if (price.compareTo(MIN_PRICE) < 0) {
            throw new ValidationException(
                    String.format("Price %s is below minimum allowed price %s", price, MIN_PRICE)
            );
        }

        if (price.compareTo(MAX_PRICE) > 0) {
            throw new ValidationException(
                    String.format("Price %s exceeds maximum allowed price %s", price, MAX_PRICE)
            );
        }
    }

    /**
     * Validates that a quantity is within acceptable bounds.
     *
     * @param quantity the quantity to validate
     * @throws ValidationException if the quantity is out of bounds
     */
    private void validateQuantity(BigDecimal quantity) {
        if (quantity.compareTo(MIN_QUANTITY) < 0) {
            throw new ValidationException(
                    String.format("Quantity %s is below minimum allowed quantity %s", quantity, MIN_QUANTITY)
            );
        }

        if (quantity.compareTo(MAX_QUANTITY) > 0) {
            throw new ValidationException(
                    String.format("Quantity %s exceeds maximum allowed quantity %s", quantity, MAX_QUANTITY)
            );
        }
    }

    /**
     * Exception thrown when an order fails validation.
     */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}
