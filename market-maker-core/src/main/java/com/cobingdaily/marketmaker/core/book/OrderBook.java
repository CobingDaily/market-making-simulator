package com.cobingdaily.marketmaker.core.book;

import com.cobingdaily.marketmaker.core.model.Order;
import com.cobingdaily.marketmaker.core.model.Side;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

/**
 * Core interface for order book operations in a market-making system.
 *
 * <p>An order book maintains buy and sell orders organized by price and time priority.
 * Buy orders (bids) are sorted by the highest price first, while sell orders (asks) are
 * sorted by the lowest price first. Orders at the same price level are processed in
 * time priority (FIFO).
 *
 * @see Order
 * @see Side
 * @see PriceLevel
 */
public interface OrderBook {

    /**
     * Adds an order to the order book.
     *
     * <p>The order will be placed in the appropriate side of the book (bid or ask)
     * and sorted by price-time priority. Orders with the same price are processed
     * in FIFO order.
     *
     * @param order the order to add, must not be null
     * @throws IllegalArgumentException if the order is invalid (null, non-positive quantity,
     *                                  duplicate order ID, or missing required fields)
     * @throws NullPointerException if the order is null
     */
    void addOrder(Order order);

    /**
     * Removes an order from the order book by its unique identifier.
     *
     * <p>If the order is found and removed, the corresponding price level will be
     * updated. Empty price levels are automatically cleaned up.
     *
     * @param orderId the unique identifier of the order to remove must not be null
     * @throws NullPointerException if orderId is null
     */
    void removeOrder(String orderId);

    /**
     * Retrieves an order by its unique identifier.
     *
     * @param orderId the unique identifier of the order must not be null
     * @return an Optional containing the order if found, empty otherwise
     * @throws NullPointerException if orderId is null
     */
    Optional<Order> getOrder(String orderId);

    /**
     * Retrieves all orders at a specific price level.
     *
     * <p>Returns a snapshot of orders at the specified price in time priority order.
     * Modifications to the returned queue do not affect the order book.
     *
     * @param side the side of the book (BUY or SELL) must not be null
     * @param price the price level to query must not be null
     * @return a queue of orders at the specified price, or null if no orders exist at that price
     * @throws NullPointerException if side or price is null
     */
    Queue<Order> getOrdersAtPrice(Side side, BigDecimal price);

    /**
     * Gets the total quantity available at a specific price level.
     *
     * <p>This represents the sum of all order quantities at the given price.
     * Useful for analyzing market depth and liquidity.
     *
     * @param side the side of the book (BUY or SELL) must not be null
     * @param price the price level to query must not be null
     * @return the total quantity at that price level, or zero if no orders exist
     * @throws NullPointerException if side or price is null
     */
    BigDecimal getQuantityAtPrice(Side side, BigDecimal price);

    /**
     * Gets the number of distinct price levels on the specified side.
     *
     * <p>Each price level may contain multiple orders. This method returns
     * the count of unique prices, not the total number of orders.
     *
     * @param side the side of the book (BUY or SELL) must not be null
     * @return the number of price levels (0 if empty)
     * @throws NullPointerException if the side is null
     */
    int getPriceLevelCount(Side side);

    /**
     * Gets a snapshot of the top price levels for the specified side.
     *
     * <p>Returns price levels in priority order (highest bids first, lowest asks first).
     * This is useful for market depth analysis and building order book displays.
     *
     * <p><strong>Note:</strong> This method returns {@link PriceLevel} objects which are
     * implementation-specific. Alternative implementations may need to provide
     * equivalent functionality using different data structures.
     *
     * @param side the side of the book (BUY or SELL) must not be null
     * @param levels the maximum number of levels to return must be positive
     * @return a list of price levels in priority order, empty if no levels exist or levels ≤ 0
     * @throws NullPointerException if the side is null
     */
    List<PriceLevel> getTopPriceLevels(Side side, int levels);

    /**
     * Gets the best (highest) bid price in the order book.
     *
     * <p>The best bid represents the highest price that buyers are willing to pay.
     *
     * @return an Optional containing the best bid price, empty if no bids exist
     */
    Optional<BigDecimal> getBestBid();

    /**
     * Gets the best (lowest) ask price in the order book.
     *
     * <p>The best ask represents the lowest price that sellers are willing to accept.
     *
     * @return an Optional containing the best ask price, empty if no asks exist
     */
    Optional<BigDecimal> getBestAsk();

    /**
     * Calculates the bid-ask spread.
     *
     * <p>The spread is the difference between the best ask and best bid prices.
     * A tighter spread generally indicates higher liquidity.
     *
     * @return an Optional containing the spread (ask - bid), empty if either the best bid or the best ask is missing
     */
    Optional<BigDecimal> getSpread();

    /**
     * Gets the total number of orders on the specified side.
     *
     * <p>This counts all individual orders across all price levels on the given side.
     *
     * @param side the side of the book (BUY or SELL) must not be null
     * @return the total number of orders (0 if empty)
     * @throws NullPointerException if the side is null
     */
    int getMarketDepth(Side side);

    /**
     * Gets the number of orders within the top N price levels on the specified side.
     *
     * <p>This method is useful for analyzing liquidity at the top of the book.
     * For example, {@code getMarketDepth(Side.BUY, 3)} returns the total number
     * of buy orders in the three highest-priced levels.
     *
     * @param side the side of the book (BUY or SELL) must not be null
     * @param levels the number of price levels to include, must be positive
     * @return the number of orders in the top N levels (0 if levels ≤ 0 or no orders exist)
     * @throws NullPointerException if the side is null
     */
    int getMarketDepth(Side side, int levels);

    /**
     * Gets the total number of orders across both sides of the book.
     *
     * <p>This is equivalent to {@code getMarketDepth(Side.BUY) + getMarketDepth(Side.SELL)}.
     *
     * @return the total number of orders in the entire book
     */
    int getTotalOrderCount();

    /**
     * Checks whether the order book is empty.
     *
     * <p>An order book is considered empty if it contains no orders on either side.
     *
     * @return true if the book contains no orders, false otherwise
     */
    boolean isEmpty();

    /**
     * Removes all orders from the order book.
     *
     * <p>After this operation, the book will be empty and all price levels will be cleared.
     * This operation is typically used for testing or when reinitializing the book.
     *
     * <p><strong>Warning:</strong> This operation cannot be undone. All order information
     * will be permanently lost.
     */
    void clear();
}
