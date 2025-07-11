package com.cobingdaily.marketmaker.strategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable representation of capital allocation state.
 *
 * <p>This record captures how capital is distributed between available funds,
 * reserved capital for pending orders, and capital locked in open positions.
 *
 * @param totalCapital the total capital allocated to the strategy
 * @param availableCapital capital available for new orders
 * @param reservedCapital capital reserved for pending orders
 * @param positionCapital capital locked in open positions
 * @param realizedPnL cumulative realized profit/loss
 * @param lastUpdateTime when the allocation was last updated
 */
public record CapitalAllocation(
        BigDecimal totalCapital,
        BigDecimal availableCapital,
        BigDecimal reservedCapital,
        BigDecimal positionCapital,
        BigDecimal realizedPnL,
        Instant lastUpdateTime
) {

    /**
     * Creates a new capital allocation with validation.
     */
    public CapitalAllocation {
        Objects.requireNonNull(totalCapital, "Total capital cannot be null");
        Objects.requireNonNull(availableCapital, "Available capital cannot be null");
        Objects.requireNonNull(reservedCapital, "Reserved capital cannot be null");
        Objects.requireNonNull(positionCapital, "Position capital cannot be null");
        Objects.requireNonNull(realizedPnL, "Realized P&L cannot be null");
        Objects.requireNonNull(lastUpdateTime, "Last update time cannot be null");

        if (totalCapital.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total capital cannot be negative");
        }
        if (availableCapital.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Available capital cannot be negative");
        }
        if (reservedCapital.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Reserved capital cannot be negative");
        }
        if (positionCapital.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Position capital cannot be negative");
        }

        // Verify capital accounting
        var totalUsed = availableCapital.add(reservedCapital).add(positionCapital);
        var expectedTotal = totalCapital.add(realizedPnL);
        if (totalUsed.compareTo(expectedTotal) != 0) {
            throw new IllegalArgumentException(
                    "Capital allocation mismatch: available + reserved + position != total + pnl"
            );
        }
    }

    /**
     * Creates an initial capital allocation.
     *
     * @param initialCapital the initial capital amount
     * @return a new allocation with all capital available
     * @throws NullPointerException if initialCapital is null
     * @throws IllegalArgumentException if initialCapital is negative
     */
    public static CapitalAllocation initial(BigDecimal initialCapital) {
        Objects.requireNonNull(initialCapital, "Initial capital cannot be null");
        if (initialCapital.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Initial capital cannot be negative");
        }

        return new CapitalAllocation(
                initialCapital,
                initialCapital,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.now()
        );
    }

    /**
     * Gets the total used capital (reserved + position).
     *
     * @return the sum of reserved and position capital
     */
    public BigDecimal getUsedCapital() {
        return reservedCapital.add(positionCapital);
    }

    /**
     * Gets the effective total capital (initial + realized P&L).
     *
     * @return the total capital adjusted for realized P&L
     */
    public BigDecimal getEffectiveCapital() {
        return totalCapital.add(realizedPnL);
    }

    /**
     * Calculates capital utilization as a percentage.
     *
     * @return utilization from 0 to 100
     */
    public BigDecimal getUtilization() {
        var effective = getEffectiveCapital();
        if (effective.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.valueOf(100); // Fully utilized if no capital
        }

        return getUsedCapital()
                .divide(effective, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Checks if there's sufficient available capital.
     *
     * @param amount the amount to check
     * @return true if sufficient capital is available
     */
    public boolean hasSufficientCapital(BigDecimal amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        return availableCapital.compareTo(amount) >= 0;
    }
}