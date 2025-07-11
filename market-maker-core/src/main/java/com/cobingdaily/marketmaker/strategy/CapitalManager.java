package com.cobingdaily.marketmaker.strategy;

import com.cobingdaily.marketmaker.core.model.Trade;

import java.math.BigDecimal;

/**
 * Manages capital allocation and usage for trading strategies.
 *
 * <p>This interface defines operations for tracking available capital,
 * reserving capital for orders, and updating capital usage based on
 * executed trades.
 *
 * <p>Implementations must be thread-safe for concurrent access.
 *
 * @see CapitalAllocation
 */
public interface CapitalManager {

    /**
     * Gets the total capital allocated to the strategy.
     *
     * @return the total capital amount
     */
    BigDecimal getTotalCapital();

    /**
     * Gets the currently available capital for new orders.
     *
     * @return the available capital amount
     */
    BigDecimal getAvailableCapital();

    /**
     * Gets the capital currently in use (reserved or in positions).
     *
     * @return the used capital amount
     */
    BigDecimal getUsedCapital();

    /**
     * Gets detailed capital allocation information.
     *
     * @return the current capital allocation
     */
    CapitalAllocation getCapitalAllocation();

    /**
     * Attempts to reserve capital for an order.
     *
     * @param amount the amount to reserve
     * @return true if capital was reserved, false if insufficient capital
     * @throws IllegalArgumentException if amount is negative
     */
    boolean reserveCapital(BigDecimal amount);

    /**
     * Releases previously reserved capital.
     *
     * @param amount the amount to release
     * @throws IllegalArgumentException if amount is negative
     */
    void releaseCapital(BigDecimal amount);

    /**
     * Updates capital usage based on an executed trade.
     *
     * @param trade the executed trade
     * @throws NullPointerException if trade is null
     */
    void updateCapitalUsage(Trade trade);

    /**
     * Gets the capital utilization as a percentage.
     *
     * @return utilization from 0 to 100
     */
    BigDecimal getCapitalUtilization();

    /**
     * Resets capital allocation to initial state.
     */
    void reset();
}