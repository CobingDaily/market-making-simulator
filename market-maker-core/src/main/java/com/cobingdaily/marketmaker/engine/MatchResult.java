package com.cobingdaily.marketmaker.engine;

import com.cobingdaily.marketmaker.core.model.Order;
import com.cobingdaily.marketmaker.core.model.OrderStatus;
import com.cobingdaily.marketmaker.core.model.Trade;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the result of processing an order through the matching engine.
 *
 * <p>This immutable value object contains all the information about what
 * happened when an order was processed, including any trades generated,
 * the remaining unfilled portion of the order, and the final status.
 *
 * @param trades list of trades generated during matching (never null, may be empty)
 * @param remainingOrder the unfilled portion of the order (empty if fully filled)
 * @param finalStatus the status of the order after processing
 * @param filledQuantity the total quantity that was filled
 * @param averagePrice the volume-weighted average execution price (null if no fills)
 */
public record MatchResult(
        List<Trade> trades,
        Optional<Order> remainingOrder,
        OrderStatus finalStatus,
        BigDecimal filledQuantity,
        BigDecimal averagePrice
) {

    public MatchResult {
        Objects.requireNonNull(trades, "Trades list cannot be null");
        Objects.requireNonNull(remainingOrder, "Remaining order optional cannot be null");
        Objects.requireNonNull(finalStatus, "Final status cannot be null");
        Objects.requireNonNull(filledQuantity, "Filled quantity cannot be null");

        if (filledQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Filled quantity cannot be negative");
        }

        if (filledQuantity.compareTo(BigDecimal.ZERO) > 0 && averagePrice == null) {
            throw new IllegalArgumentException("Average price required when quantity is filled");
        }

        if (filledQuantity.compareTo(BigDecimal.ZERO) == 0 && !trades.isEmpty()) {
            throw new IllegalArgumentException("Cannot have trades with zero filled quantity");
        }

        // Make an immutable copy of the trade list
        trades = List.copyOf(trades);
    }

    /**
     * Creates a result for an order that couldn't be matched.
     *
     * @param order the unmatched order
     * @return a match result with no trades
     */
    public static MatchResult noMatch(Order order) {
        return new MatchResult(
                List.of(),
                Optional.of(order),
                OrderStatus.NEW,
                BigDecimal.ZERO,
                null
        );
    }

    /**
     * Creates a result for a fully filled order.
     *
     * @param trades the trades that filled the order
     * @param averagePrice the volume-weighted average execution price
     * @return a match result with no remaining order
     */
    public static MatchResult fullyFilled(List<Trade> trades, BigDecimal averagePrice) {
        Objects.requireNonNull(trades, "Trades cannot be null");
        Objects.requireNonNull(averagePrice, "Average price cannot be null");

        if (trades.isEmpty()) {
            throw new IllegalArgumentException("Cannot be fully filled with no trades");
        }

        BigDecimal totalQuantity = trades.stream()
                .map(Trade::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MatchResult(
                trades,
                Optional.empty(),
                OrderStatus.FILLED,
                totalQuantity,
                averagePrice
        );
    }

    /**
     * Creates a result for a partially filled order.
     *
     * @param trades the trades that partially filled the order
     * @param remainingOrder the unfilled portion
     * @param averagePrice the volume-weighted average execution price
     * @return a match result with remaining order
     */
    public static MatchResult partiallyFilled(List<Trade> trades, Order remainingOrder, BigDecimal averagePrice) {
        Objects.requireNonNull(trades, "Trades cannot be null");
        Objects.requireNonNull(remainingOrder, "Remaining order cannot be null");
        Objects.requireNonNull(averagePrice, "Average price cannot be null");

        if (trades.isEmpty()) {
            throw new IllegalArgumentException("Cannot be partially filled with no trades");
        }

        BigDecimal filledQuantity = trades.stream()
                .map(Trade::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MatchResult(
                trades,
                Optional.of(remainingOrder),
                OrderStatus.PARTIALLY_FILLED,
                filledQuantity,
                averagePrice
        );
    }

    /**
     * Checks if any trades were executed.
     *
     * @return true if at least one trade was executed
     */
    public boolean hasExecutions() {
        return !trades.isEmpty();
    }

    /**
     * Checks if the order was fully filled.
     *
     * @return true if the order was completely filled
     */
    public boolean isFullyFilled() {
        return finalStatus == OrderStatus.FILLED;
    }

    /**
     * Checks if the order was partially filled.
     *
     * @return true if the order was partially filled
     */
    public boolean isPartiallyFilled() {
        return finalStatus == OrderStatus.PARTIALLY_FILLED;
    }

    /**
     * Gets the total number of trades executed.
     *
     * @return the number of trades
     */
    public int getTradeCount() {
        return trades.size();
    }
}