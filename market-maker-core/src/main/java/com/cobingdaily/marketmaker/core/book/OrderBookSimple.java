package com.cobingdaily.marketmaker.core.book;

import com.cobingdaily.marketmaker.core.model.Order;
import com.cobingdaily.marketmaker.core.model.Side;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OrderBookSimple implements OrderBook{
    private final TreeMap<BigDecimal, Queue<Order>> bids;
    private final TreeMap<BigDecimal, Queue<Order>> asks;
    private final Map<String, Order> orderMap;

    public OrderBookSimple() {
        bids = new TreeMap<>(Collections.reverseOrder());
        asks = new TreeMap<>();
        orderMap = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<Order> getOrder(String orderId) {
        var order = orderMap.get(orderId);
        if (order == null)
            return Optional.empty();

        return Optional.of(order);
    }

    @Override
    public Queue<Order> getOrdersAtPrice(Side side, BigDecimal price) {
        var book = getBookForSide(side);

        return book.get(price);
    }

    @Override
    public void addOrder(Order order) {
        boolean invalidOrder = order == null || order.quantity().compareTo(BigDecimal.ZERO) <= 0;
        if (invalidOrder)
            throw new IllegalArgumentException("Invalid Order");

        var book = getBookForSide(order.side());

        book.computeIfAbsent(order.price(), x -> new LinkedList<>()).offer(order);

        orderMap.put(order.orderId(), order);
    }

    @Override
    public void removeOrder(String orderId) {
        Order order = orderMap.remove(orderId);
        if (order == null) {
            return;
        }

        var book = getBookForSide(order.side());
        var level = book.get(order.price());

        if (level != null) {
            level.remove(order);

            // Remove empty price levels
            if (level.isEmpty()) {
                book.remove(order.price());
            }
        }
    }

    @Override
    public Optional<BigDecimal> getBestBid() {
        if (bids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(bids.firstKey());
    }

    @Override
    public Optional<BigDecimal> getBestAsk() {
        if (asks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(asks.firstKey());
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
        var book = getBookForSide(side);

        return book.values()
                .stream()
                .mapToInt(Collection::size)
                .sum();
    }

    @Override
    public int getMarketDepth(Side side, int levels) {
        var book = getBookForSide(side);

        return book.values()
                .stream()
                .limit(levels)
                .mapToInt(Collection::size)
                .sum();
    }

    private TreeMap<BigDecimal, Queue<Order>> getBookForSide(Side side) {
        return side == Side.BUY ? bids : asks;
    }
}
