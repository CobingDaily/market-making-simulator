package com.cobingdaily.marketmaker.engine;

import com.cobingdaily.marketmaker.core.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Generates Trade records when orders are matched.
 *
 * <p>This class encapsulates the logic for creating trades, including:
 * <ul>
 *   <li>Determining the execution price (passive order's price)</li>
 *   <li>Calculating the traded quantity</li>
 *   <li>Identifying buyer and seller</li>
 *   <li>Setting the trade timestamp</li>
 * </ul>
 *
 * <p>Price determination follows standard exchange rules where the
 * passive order (the one already in the book) sets the execution price.
 */
public class TradeGenerator {

    /**
     * Generates a trade from two matching orders.
     *
     * <p>The execution price is determined by the passive order (the one
     * that was already in the order book). The trade quantity is the
     * minimum of the two order quantities.
     *
     * @param aggressorOrder the incoming order that triggered the match
     * @param passiveOrder the existing order in the book
     * @param quantity the quantity to trade (must not exceed either order's quantity)
     * @return a new Trade record
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if orders are on the same side or quantity is invalid
     */
    public Trade generateTrade(Order aggressorOrder, Order passiveOrder, BigDecimal quantity) {
        Objects.requireNonNull(aggressorOrder, "Aggressor order cannot be null");
        Objects.requireNonNull(passiveOrder, "Passive order cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");

        if (aggressorOrder.side() == passiveOrder.side()) {
            throw new IllegalArgumentException("Cannot generate trade between orders on the same side");
        }

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Trade quantity must be positive");
        }

        if (quantity.compareTo(aggressorOrder.quantity()) > 0) {
            throw new IllegalArgumentException("Trade quantity exceeds aggressor order quantity");
        }

        if (quantity.compareTo(passiveOrder.quantity()) > 0) {
            throw new IllegalArgumentException("Trade quantity exceeds passive order quantity");
        }

        BigDecimal executionPrice = determineExecutionPrice(aggressorOrder, passiveOrder);

        String buyer = aggressorOrder.side() == Side.BUY ? aggressorOrder.traderId() : passiveOrder.traderId();
        String seller = aggressorOrder.side() == Side.SELL ? aggressorOrder.traderId() : passiveOrder.traderId();

        return new Trade(
                buyer,
                seller,
                executionPrice,
                quantity,
                Instant.now()
        );
    }

    /**
     * Determines the execution price for a trade.
     *
     * <p>The execution price is always the passive order's price, following
     * standard exchange rules. For market orders matched against limit orders,
     * the limit order's price is used.
     *
     * @param aggressorOrder the incoming order
     * @param passiveOrder the existing order
     * @return the execution price
     * @throws IllegalStateException if price cannot be determined
     */
    public BigDecimal determineExecutionPrice(Order aggressorOrder, Order passiveOrder) {
        // Passive order takes priority (price-time priority rule)
        if (passiveOrder.type() == OrderType.LIMIT) {
            return passiveOrder.price();
        }

        // If passive order is market order, use aggressor order price
        if (aggressorOrder.type() == OrderType.LIMIT) {
            return aggressorOrder.price();
        }

        // Both market orders - this should not happen as the order book keeps limit orders stored
        throw new IllegalStateException("Cannot determine price for two market orders");
    }

    /**
     * Calculates the maximum quantity that can be traded between two orders.
     *
     * @param order1 first order
     * @param order2 second order
     * @return the minimum of the two quantities
     */
    public BigDecimal calculateMatchQuantity(Order order1, Order order2) {
        Objects.requireNonNull(order1, "Order1 cannot be null");
        Objects.requireNonNull(order2, "Order2 cannot be null");

        return order1.quantity().min(order2.quantity());
    }
}
