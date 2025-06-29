package com.cobingdaily.marketmaker.core.book;

import com.cobingdaily.marketmaker.core.model.Order;
import com.cobingdaily.marketmaker.core.model.Side;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple order book implementation using PriceLevel
 *
 * <ul>
 *   <li>Price-time priority for order matching</li>
 *   <li>O(log n) insertion and best price lookup</li>
 *   <li>O(1) order lookup by ID</li>
 * </ul>
 */
public class OrderBookSimple implements OrderBook{
    private final TreeMap<BigDecimal, PriceLevel> bids;
    private final TreeMap<BigDecimal, PriceLevel> asks;
    private final Map<String, OrderLocation> orderLocationMap;

    /** Helper record to track where an order is located in the book. */
    private record OrderLocation(Order order, PriceLevel priceLevel) {}

    public OrderBookSimple() {
        bids = new TreeMap<>(Collections.reverseOrder());
        asks = new TreeMap<>();
        orderLocationMap = new ConcurrentHashMap<>();
    }

    @Override
    public void addOrder(Order order) {
        validateOrder(order);

        var book = getBookForSide(order.side());
        var priceLevel = book.computeIfAbsent(order.price(), PriceLevel::new);

        priceLevel.addOrder(order);
        orderLocationMap.put(order.orderId(), new OrderLocation(order, priceLevel));
    }

    @Override
    public void removeOrder(String orderId) {
        Objects.requireNonNull(orderId, "Order ID cannot be null");

        var location = orderLocationMap.remove(orderId);
        if (location == null) {
            return;
        }

        var priceLevel = location.priceLevel();
        var removedOrder = priceLevel.removeOrder(orderId);

        if (removedOrder != null && priceLevel.isEmpty()) {
            var book = getBookForSide(removedOrder.side());
            book.remove(removedOrder.price());
        }
    }

    @Override
    public Optional<Order> getOrder(String orderId) {
        Objects.requireNonNull(orderId, "Order ID cannot be null");

        var location = orderLocationMap.get(orderId);
        if (location == null)
            return Optional.empty();

        return Optional.of(location.order());
    }

    @Override
    public Queue<Order> getOrdersAtPrice(Side side, BigDecimal price) {
        Objects.requireNonNull(side, "Side cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");

        var priceLevel = getBookForSide(side).get(price);
        return priceLevel == null ? null : priceLevel.getOrders();
    }

    @Override
    public BigDecimal getQuantityAtPrice(Side side, BigDecimal price) {
        Objects.requireNonNull(side, "Side cannot be null");
        Objects.requireNonNull(price, "Price cannot be null");

        var priceLevel = getBookForSide(side).get(price);
        return priceLevel == null ? BigDecimal.ZERO : priceLevel.getTotalQuantity();
    }

    @Override
    public int getPriceLevelCount(Side side) {
        return getBookForSide(side).size();
    }

    @Override
    public List<PriceLevel> getTopPriceLevels(Side side, int levels) {
        if (levels <= 0) {
            return Collections.emptyList();
        }

        return getBookForSide(side)
                .values()
                .stream()
                .limit(levels)
                .toList();
    }

    @Override
    public Optional<BigDecimal> getBestBid() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstKey());
    }

    @Override
    public Optional<BigDecimal> getBestAsk() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstKey());
    }

    @Override
    public Optional<BigDecimal> getSpread() {
        var bestBid = getBestBid();
        var bestAsk = getBestAsk();

        if (bestBid.isPresent() && bestAsk.isPresent()) {
            return Optional.of(bestAsk.get().subtract(bestBid.get()));
        }

        return Optional.empty();
    }

    @Override
    public int getMarketDepth(Side side) {
        return getBookForSide(side)
                .values()
                .stream()
                .mapToInt(PriceLevel::getOrderCount)
                .sum();
    }

    @Override
    public int getMarketDepth(Side side, int levels) {
        if (levels <= 0) {
            return 0;
        }

        return getBookForSide(side)
                .values()
                .stream()
                .limit(levels)
                .mapToInt(PriceLevel::getOrderCount)
                .sum();
    }

    @Override
    public int getTotalOrderCount() {
        return getMarketDepth(Side.BUY) + getMarketDepth(Side.SELL);
    }

    @Override
    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }

    @Override
    public void clear() {
        bids.clear();
        asks.clear();
        orderLocationMap.clear();
    }

    private TreeMap<BigDecimal, PriceLevel> getBookForSide(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    private void validateOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        if (order.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Order quantity must be positive");
        }

        if (order.orderId() == null || order.orderId().isBlank()) {
            throw new IllegalArgumentException("Order ID cannot be null or blank");
        }

        // Ensure Order ID uniqueness
        if (orderLocationMap.containsKey(order.orderId())) {
            throw new IllegalArgumentException("Order with ID " + order.orderId() + " already exists");
        }
    }

    @Override
    public String toString() {
        return String.format("OrderBook{bids=%d levels, asks=%d levels, total orders=%d}",
                getPriceLevelCount(Side.BUY),
                getPriceLevelCount(Side.SELL),
                getTotalOrderCount());
    }
}
