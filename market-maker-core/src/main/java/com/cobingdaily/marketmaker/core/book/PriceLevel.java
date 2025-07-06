package com.cobingdaily.marketmaker.core.book;

import com.cobingdaily.marketmaker.core.model.Order;

import java.math.BigDecimal;
import java.util.Queue;

/**
 * Represents all orders at a specific price level in the order book.
 *
 * <p>This interface defines operations for managing orders at a single price point.
 *
 * <p>Implementations should be thread-safe for concurrent access.
 */
public interface PriceLevel {

    /**
     * Gets the price of this level.
     *
     * @return the price
     */
    BigDecimal getPrice();

    /**
     * Gets a copy of all orders at this price level.
     * The returned queue is a snapshot, and modifications won't affect the price level.
     *
     * @return new queue containing all orders in time priority
     */
    Queue<Order> getOrders();

    /**
     * Gets the total quantity of all orders at this level.
     *
     * @return sum of all order quantities
     */
    BigDecimal getTotalQuantity();

    /**
     * Gets the number of orders at this level.
     *
     * @return order count
     */
    int getOrderCount();

    /**
     * Checks if this price level has no orders.
     *
     * @return true if empty
     */
    boolean isEmpty();

    /**
     * Gets the first order without removing it.
     *
     * @return the first order, or null if empty
     */
    Order peekFirst();

    /**
     * Removes and returns the first order.
     *
     * @return the removed order, or null if empty
     */
    Order pollFirst();

    /**
     * Adds an order to this price level.
     *
     * @param order the order to add
     * @throws NullPointerException if the order is null
     * @throws IllegalArgumentException if the order price doesn't match the level price
     */
    void addOrder(Order order);

    /**
     * Removes a specific order from this price level.
     *
     * @param orderId the ID of the order to remove
     * @return the removed order, or null if not found
     */
    Order removeOrder(String orderId);
}