package com.cobingdaily.marketmaker.engine;

import com.cobingdaily.marketmaker.core.model.Order;
import com.cobingdaily.marketmaker.core.model.OrderStatus;
import java.math.BigDecimal;

/**
 * Core interface for order matching engine operations.
 *
 * <p>The matching engine is responsible for processing incoming orders against
 * the order book, executing trades when prices cross, handling partial fills,
 * and maintaining order status tracking.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Match incoming orders against the order book</li>
 *   <li>Execute trades using price-time priority</li>
 *   <li>Handle partial fills and order status updates</li>
 *   <li>Generate trade records for executed matches</li>
 * </ul>
 *
 * @see Order
 * @see MatchResult
 */
public interface MatchingEngine {

    /**
     * Processes an incoming order against the order book.
     *
     * <p>This method attempts to match the order against existing orders
     * on the opposite side of the book. For buy orders, it matches against
     * sell orders starting from the lowest ask. For sell orders, it matches
     * against buy orders starting from the highest bid.
     *
     * <p>The matching process follows these rules:
     * <ul>
     *   <li>Market orders match at any available price</li>
     *   <li>Limit orders only match when prices cross (bid ≥ ask)</li>
     *   <li>Orders are matched using price-time priority</li>
     *   <li>Partial fills are supported when quantities don't match exactly</li>
     * </ul>
     *
     * @param incomingOrder the order to process, must not be null
     * @return the result of the matching attempt, including trades and remaining order
     * @throws NullPointerException if the order is null
     * @throws IllegalArgumentException if the order is invalid
     */
    MatchResult processOrder(Order incomingOrder);

    /**
     * Gets the current status of an order.
     *
     * <p>Since Order objects are immutable, status is tracked separately
     * by the matching engine.
     *
     * @param orderId the unique identifier of the order
     * @return the current order status, or NEW if not tracked
     * @throws NullPointerException if orderId is null
     */
    OrderStatus getOrderStatus(String orderId);

    /**
     * Cancels an order, removing it from the order book.
     *
     * <p>This method removes the order from the book and updates its
     * status to CANCELLED. Partially filled orders can be cancelled,
     * in which case only the remaining quantity is removed.
     *
     * @param orderId the unique identifier of the order to cancel
     * @return true if the order was cancelled, false if not found
     * @throws NullPointerException if orderId is null
     */
    boolean cancelOrder(String orderId);

    /**
     * Gets the total traded volume for a specific trader.
     *
     * <p>This includes both buy and sell volume from all executed trades.
     *
     * @param traderId the identifier of the trader
     * @return the total traded volume, or zero if no trades found
     * @throws NullPointerException if traderId is null
     */
    BigDecimal getTradedVolume(String traderId);
}