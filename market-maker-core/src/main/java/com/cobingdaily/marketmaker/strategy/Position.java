package com.cobingdaily.marketmaker.strategy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable representation of a trading position.
 *
 * <p>This record captures the current state of a trading position including
 * the net quantity, average cost, realized and unrealized P&L, and timing
 * information.
 *
 * @param netQuantity the net position (positive for long, negative for short)
 * @param totalBought total quantity bought
 * @param totalSold total quantity sold
 * @param avgBuyPrice volume-weighted average buy price
 * @param avgSellPrice volume-weighted average sell price
 * @param realizedPnL profit/loss from closed positions
 * @param lastUpdateTime when the position was last updated
 * @param openTime when the position was first opened
 * @param turnover total traded volume (bought + sold)
 */
public record Position(
        BigDecimal netQuantity,
        BigDecimal totalBought,
        BigDecimal totalSold,
        BigDecimal avgBuyPrice,
        BigDecimal avgSellPrice,
        BigDecimal realizedPnL,
        Instant lastUpdateTime,
        Instant openTime,
        BigDecimal turnover
) {

    /**
     * Creates a new position with validation.
     */
    public Position {
        Objects.requireNonNull(netQuantity, "Net quantity cannot be null");
        Objects.requireNonNull(totalBought, "Total bought cannot be null");
        Objects.requireNonNull(totalSold, "Total sold cannot be null");
        Objects.requireNonNull(avgBuyPrice, "Average buy price cannot be null");
        Objects.requireNonNull(avgSellPrice, "Average sell price cannot be null");
        Objects.requireNonNull(realizedPnL, "Realized P&L cannot be null");
        Objects.requireNonNull(lastUpdateTime, "Last update time cannot be null");
        Objects.requireNonNull(openTime, "Open time cannot be null");
        Objects.requireNonNull(turnover, "Turnover cannot be null");

        if (totalBought.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total bought cannot be negative");
        }
        if (totalSold.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total sold cannot be negative");
        }
        if (turnover.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Turnover cannot be negative");
        }

        var calculatedNet = totalBought.subtract(totalSold);
        if (netQuantity.compareTo(calculatedNet) != 0) {
            throw new IllegalArgumentException("Net quantity doesn't match bought - sold");
        }
    }

    /**
     * Creates an empty position.
     *
     * @return a new position with zero quantities
     */
    public static Position empty() {
        var now = Instant.now();
        return new Position(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                now,
                now,
                BigDecimal.ZERO
        );
    }

    /**
     * Checks if this is a long position (net bought).
     *
     * @return true if net quantity is positive
     */
    public boolean isLong() {
        return netQuantity.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Checks if this is a short position (net sold).
     *
     * @return true if net quantity is negative
     */
    public boolean isShort() {
        return netQuantity.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Checks if this is a flat position (no inventory).
     *
     * @return true if net quantity is zero
     */
    public boolean isFlat() {
        return netQuantity.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Gets the absolute position size.
     *
     * @return the absolute value of net quantity
     */
    public BigDecimal getAbsoluteSize() {
        return netQuantity.abs();
    }

    /**
     * Calculates the average entry price for the current position.
     *
     * @return the average price, or zero if flat
     */
    public BigDecimal getAverageEntryPrice() {
        if (isLong()) {
            return avgBuyPrice;
        }
        else if (isShort()) {
            return avgSellPrice;
        }

        return BigDecimal.ZERO;
    }

    /**
     * Calculates unrealized P&L given current market price.
     *
     * @param currentPrice the current market price
     * @return unrealized profit or loss
     * @throws NullPointerException if currentPrice is null
     */
    public BigDecimal calculateUnrealizedPnL(BigDecimal currentPrice) {
        Objects.requireNonNull(currentPrice, "Current price cannot be null");

        if (isFlat()) {
            return BigDecimal.ZERO;
        }

        var entryPrice = getAverageEntryPrice();
        if (isLong()) {
            return currentPrice.subtract(entryPrice).multiply(netQuantity);
        }
        else {
            return entryPrice.subtract(currentPrice).multiply(netQuantity);
        }
    }

    /**
     * Gets the total P&L (realized + unrealized).
     *
     * @param currentPrice the current market price
     * @return total profit or loss
     */
    public BigDecimal getTotalPnL(BigDecimal currentPrice) {
        return realizedPnL.add(calculateUnrealizedPnL(currentPrice));
    }

    /**
     * Gets the age of the position.
     *
     * @return duration since position was opened
     */
    public Duration getAge() {
        return Duration.between(openTime, lastUpdateTime);
    }
}
