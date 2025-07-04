package com.cobingdaily.marketmaker.engine;

import com.cobingdaily.marketmaker.core.book.OrderBook;
import com.cobingdaily.marketmaker.core.model.OrderStatus;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The main implementation of the matching engine using price-time priority.
 *
 * <p>This implementation processes orders against an order book, executing
 * trades when prices cross and handling partial fills. It maintains order
 * status tracking separately from the immutable Order objects.
 */
public class MatchingEngineImpl {
    private final OrderBook orderBook;
    private final OrderValidator orderValidator;
    private final TradeGenerator tradeGenerator;

    /** Track order statuses separately since orders are immutable objects.*/
    private final Map<String, OrderStatus> orderStatusMap;

    /** Track remaining quantities for partially filled orders. */
    private final Map<String, BigDecimal> remainingQuantityMap;

    /** Track volumes of traders. */
    private final Map<String, AtomicReference<BigDecimal>> traderVolumeMap;

    /**
     * Creates a new matching engine with the specified order book.
     *
     * @param orderBook the order book to use for matching
     * @throws NullPointerException if orderBook is null
     */
    public MatchingEngineImpl(OrderBook orderBook) {
        this.orderBook = Objects.requireNonNull(orderBook, "Order book cannot be null");
        orderValidator = new OrderValidator();
        tradeGenerator = new TradeGenerator();
        orderStatusMap = new ConcurrentHashMap<>();
        remainingQuantityMap = new ConcurrentHashMap<>();
        traderVolumeMap = new ConcurrentHashMap<>();
    }
}
