package com.cobingdaily.marketmaker.core.book;

import com.cobingdaily.marketmaker.core.model.Order;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Represents all orders at a specific price level in the order book.
 *
 * <p>This class maintains orders in time priority (FIFO) and provides
 * efficient operations for managing orders at a single price point.
 *
 * <p>Thread-safety: This class is thread-safe for concurrent access.
 */
public class PriceLevel {
    private final BigDecimal price;
    private final Queue<Order> orders;
    private final LongAdder totalQuantity;
    private final AtomicInteger orderCount;

    /**
     * Creates a new empty price level.
     *
     * @param price the price for this level
     * @throws NullPointerException if price is null
     */
    public PriceLevel(BigDecimal price) {
        this.price = Objects.requireNonNull(price, "Price cannot be null");
        this.orders = new ConcurrentLinkedQueue<>();
        this.totalQuantity = new LongAdder();
        this.orderCount = new AtomicInteger(0);
    }

    /**
     * Gets the price of this level.
     *
     * @return the price
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Gets a copy of all orders at this price level.
     * The returned queue is a snapshot, and modifications won't affect the price level.
     *
     * @return new queue containing all orders in time priority
     */
    public Queue<Order> getOrders() {
        return new LinkedList<>(orders);
    }

    /**
     * Gets the total quantity of all orders at this level.
     *
     * @return sum of all order quantities
     */
    public BigDecimal getTotalQuantity() {
        return BigDecimal.valueOf(totalQuantity.sum());
    }

    /**
     * Gets the number of orders at this level.
     *
     * @return order count
     */
    public int getOrderCount() {
        return orderCount.get();
    }

    /**
     * Checks if this price level has no orders.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return orderCount.get() == 0;
    }

    /**
     * Gets the first (oldest) order without removing it.
     *
     * @return the first order, or null if empty
     */
    public Order peekFirst() {
        return orders.peek();
    }

    /**
     * Removes and returns the first (oldest) order.
     *
     * @return the removed order, or null if empty
     */
    public Order pollFirst() {
        var order = orders.poll();
        if (order != null) {
            totalQuantity.add(-order.quantity().longValue());
            orderCount.decrementAndGet();
        }
        return order;
    }

    /**
     * Adds an order to this price level.
     * Orders are maintained in time priority (FIFO).
     *
     * @param order the order to add
     * @throws NullPointerException if the order is null
     * @throws IllegalArgumentException if the order price doesn't match the level price
     */
    public void addOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");

        if (!price.equals(order.price())) {
            throw new IllegalArgumentException(
                    String.format("Order price %s doesn't match level price %s",
                            order.price(), price)
            );
        }

        orders.offer(order);
        totalQuantity.add(order.price().longValue());
        orderCount.incrementAndGet();
    }

    /**
     * Removes a specific order from this price level.
     *
     * @param orderId the ID of the order to remove
     * @return the removed order, or null if not found
     */
    public Order removeOrder(String orderId) {
        Objects.requireNonNull(orderId, "Order ID cannot be null");

        var iterator = orders.iterator();
        while (iterator.hasNext()) {
            var order = iterator.next();
            if (order.orderId().equals(orderId)) {
                iterator.remove();
                totalQuantity.add(-order.quantity().longValue());
                orderCount.decrementAndGet();
                return order;
            }
        }
        return null;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PriceLevel other)) return false;
        return price.compareTo(other.price) == 0;
    }

    @Override
    public int hashCode() {
        return price.hashCode();
    }

    @Override
    public String toString() {
        return String.format("PriceLevel{price=%s, orders=%d, quantity=%s}",
                getPrice(), getOrderCount(), getTotalQuantity());
    }
}
