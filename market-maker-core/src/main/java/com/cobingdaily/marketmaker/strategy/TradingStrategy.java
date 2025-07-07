package com.cobingdaily.marketmaker.strategy;

import com.cobingdaily.marketmaker.core.book.OrderBook;
import com.cobingdaily.marketmaker.core.model.*;

public interface TradingStrategy {
    void initialize(StrategyConfig config);
    void onMarketData(OrderBook orderBook);
    void onTrade(Trade trade);
    void onOrderUpdate(Order order, OrderStatus status);
    void start();
    void stop();
    boolean isRunning();
}
