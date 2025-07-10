package com.cobingdaily.marketmaker.strategy;

import com.cobingdaily.marketmaker.core.model.Order;
import com.cobingdaily.marketmaker.core.model.Trade;

import java.math.BigDecimal;

/**
 * Manages position tracking and risk limits for trading strategies.
 *
 * <p>This interface defines operations for tracking the current position
 * (inventory) of a trading strategy, enforcing position limits, and
 * calculating position-related metrics.
 *
 * <p>Implementations must be thread-safe for concurrent access.
 *
 * @see Position
 * @see Trade
 */
public interface PositionManager {

    /**
     * Gets the current net position.
     *
     * <p>Positive values indicate a long position (more bought than sold),
     * negative values indicate a short position (more sold than bought).
     *
     * @return the current net position
     */
    BigDecimal getCurrentPosition();

    /**
     * Gets detailed position information.
     *
     * @return the current position details
     */
    Position getPositionDetails();

    /**
     * Updates the position based on an executed trade.
     *
     * @param trade the executed trade
     * @throws NullPointerException if trade is null
     */
    void updatePosition(Trade trade);

    /**
     * Checks if an order can be accepted without breaching position limits.
     *
     * @param order the order to check
     * @return true if the order can be accepted, false otherwise
     * @throws NullPointerException if the order is null
     */
    boolean canAcceptOrder(Order order);

    /**
     * Gets the maximum allowed position size.
     *
     * @return the maximum position limit
     */
    BigDecimal getMaxPosition();

    /**
     * Gets the current position utilization as a percentage.
     *
     * @return utilization from 0 to 100
     */
    BigDecimal getPositionUtilization();

    /**
     * Resets the position to zero.
     * Typically used when starting a new trading session.
     */
    void reset();
}
