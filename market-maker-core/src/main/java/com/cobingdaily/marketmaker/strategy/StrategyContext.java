package com.cobingdaily.marketmaker.strategy;

import com.cobingdaily.marketmaker.core.book.OrderBook;
import com.cobingdaily.marketmaker.core.model.Order;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

public interface StrategyContext {
    CompletableFuture<Order> placeOrder(Order order);
    CompletableFuture<Boolean> cancelOrder(String orderId);
    OrderBook getOrderBook();
    BigDecimal getPosition();
    BigDecimal getAvailableCapital();
}
