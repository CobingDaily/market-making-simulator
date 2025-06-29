package com.cobingdaily.marketmaker.core.book;

import com.cobingdaily.marketmaker.core.model.Order;
import com.cobingdaily.marketmaker.core.model.Side;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Queue;

public interface OrderBook {
    void addOrder(Order order);
    void removeOrder(String orderId);
    Optional<Order> getOrder(String orderId);

    Optional<BigDecimal> getBestBid();
    Optional<BigDecimal> getBestAsk();
    Optional<BigDecimal> getSpread();

    int getMarketDepth(Side side);
    int getMarketDepth(Side side, int limit);


    Queue<Order> getOrdersAtPrice(Side side, BigDecimal price);

    //TODO: Implement getSnapshot();
}
