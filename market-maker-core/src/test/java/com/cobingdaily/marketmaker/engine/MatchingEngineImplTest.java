package com.cobingdaily.marketmaker.engine;

import com.cobingdaily.marketmaker.core.book.OrderBook;
import com.cobingdaily.marketmaker.core.book.OrderBookSimple;
import com.cobingdaily.marketmaker.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MatchingEngineImpl Tests")
class MatchingEngineImplTest {

    private MatchingEngine matchingEngine;
    private OrderBook orderBook;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBookSimple();
        matchingEngine = new MatchingEngineImpl(orderBook);
    }

    @Nested
    @DisplayName("Basic Order Processing")
    class BasicOrderProcessing {

        @Test
        @DisplayName("should process limit buy order with no match")
        void shouldProcessLimitBuyOrderNoMatch() {
            // Given
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "10");

            // When
            MatchResult result = matchingEngine.processOrder(buyOrder);

            // Then
            assertThat(result.hasExecutions()).isFalse();
            assertThat(result.finalStatus()).isEqualTo(OrderStatus.NEW);
            assertThat(result.remainingOrder()).isPresent();
            assertThat(result.filledQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(orderBook.getOrder(buyOrder.orderId())).isPresent();
        }

        @Test
        @DisplayName("should process limit sell order with no match")
        void shouldProcessLimitSellOrderNoMatch() {
            // Given
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "10");

            // When
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then
            assertThat(result.hasExecutions()).isFalse();
            assertThat(result.finalStatus()).isEqualTo(OrderStatus.NEW);
            assertThat(result.remainingOrder()).isPresent();
            assertThat(orderBook.getOrder(sellOrder.orderId())).isPresent();
        }

        @Test
        @DisplayName("should fully match crossing limit orders")
        void shouldFullyMatchCrossingLimitOrders() {
            // Given
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "10");
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "10");

            // When
            matchingEngine.processOrder(buyOrder);
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then
            assertThat(result.hasExecutions()).isTrue();
            assertThat(result.isFullyFilled()).isTrue();
            assertThat(result.trades()).hasSize(1);
            assertThat(result.filledQuantity()).isEqualByComparingTo("10");
            assertThat(result.averagePrice()).isEqualByComparingTo("100.00");
            
            Trade trade = result.trades().getFirst();
            assertThat(trade.quantity()).isEqualByComparingTo("10");
            assertThat(trade.price()).isEqualByComparingTo("100.00");
            assertThat(trade.buyer()).isEqualTo("TRADER-1");
            assertThat(trade.seller()).isEqualTo("TRADER-2");
            
            // Both orders should be removed from the book
            assertThat(orderBook.getOrder(buyOrder.orderId())).isEmpty();
            assertThat(orderBook.getOrder(sellOrder.orderId())).isEmpty();
        }

        @Test
        @DisplayName("should handle buy order with better price")
        void shouldHandleBuyOrderWithBetterPrice() {
            // Given - sell order at 100
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "10");
            matchingEngine.processOrder(sellOrder);

            // When - buy order at 101 (willing to pay more)
            Order buyOrder = createLimitOrder(Side.BUY, "101.00", "10");
            MatchResult result = matchingEngine.processOrder(buyOrder);

            // Then - should match at sell price (100)
            assertThat(result.isFullyFilled()).isTrue();
            assertThat(result.trades().getFirst().price()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("should handle sell order with better price")
        void shouldHandleSellOrderWithBetterPrice() {
            // Given - buy order at 100
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "10");
            matchingEngine.processOrder(buyOrder);

            // When - sell order at 99 (willing to sell for less)
            Order sellOrder = createLimitOrder(Side.SELL, "99.00", "10");
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then - should match at buy price (100)
            assertThat(result.isFullyFilled()).isTrue();
            assertThat(result.trades().getFirst().price()).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    @DisplayName("Price-Time Priority")
    class PriceTimePriority {

        @Test
        @DisplayName("should match orders in FIFO order at same price")
        void shouldMatchInFIFOOrder() {
            // Given - three buy orders at same price
            Order buy1 = createLimitOrder(Side.BUY, "100.00", "5", "TRADER-A");
            Order buy2 = createLimitOrder(Side.BUY, "100.00", "5", "TRADER-B");
            Order buy3 = createLimitOrder(Side.BUY, "100.00", "5", "TRADER-C");

            matchingEngine.processOrder(buy1);
            matchingEngine.processOrder(buy2);
            matchingEngine.processOrder(buy3);

            // When - sell order that matches all
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "15", "TRADER-D");
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then - should match in order: buy1, buy2, buy3
            // Note: Order book maintains FIFO ordering for orders at the same price level
            assertThat(result.trades()).hasSize(3);
            assertThat(result.trades().get(0).buyer()).isEqualTo("TRADER-A");
            assertThat(result.trades().get(1).buyer()).isEqualTo("TRADER-B");
            assertThat(result.trades().get(2).buyer()).isEqualTo("TRADER-C");
        }

        @Test
        @DisplayName("should match best prices first")
        void shouldMatchBestPricesFirst() {
            // Given - buy orders at different prices
            Order buy1 = createLimitOrder(Side.BUY, "99.00", "5", "TRADER-A");
            Order buy2 = createLimitOrder(Side.BUY, "101.00", "5", "TRADER-B");
            Order buy3 = createLimitOrder(Side.BUY, "100.00", "5", "TRADER-C");

            matchingEngine.processOrder(buy1);
            matchingEngine.processOrder(buy2);
            matchingEngine.processOrder(buy3);

            // When - sell order
            Order sellOrder = createLimitOrder(Side.SELL, "99.00", "15", "TRADER-D");
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then - should match in price order: 101, 100, 99
            assertThat(result.trades()).hasSize(3);
            assertThat(result.trades().get(0).buyer()).isEqualTo("TRADER-B");
            assertThat(result.trades().get(0).price()).isEqualByComparingTo("101.00");
            assertThat(result.trades().get(1).buyer()).isEqualTo("TRADER-C");
            assertThat(result.trades().get(1).price()).isEqualByComparingTo("100.00");
            assertThat(result.trades().get(2).buyer()).isEqualTo("TRADER-A");
            assertThat(result.trades().get(2).price()).isEqualByComparingTo("99.00");
        }
    }

    @Nested
    @DisplayName("Self-Trade Prevention")
    class SelfTradePrevention {

        @Test
        @DisplayName("should prevent self-trading")
        void shouldPreventSelfTrading() {
            // Given - buy and sell from the same trader
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "10", "TRADER-A");
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "10", "TRADER-A");

            matchingEngine.processOrder(buyOrder);

            // When
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then - no match should occur
            assertThat(result.hasExecutions()).isFalse();
            assertThat(result.finalStatus()).isEqualTo(OrderStatus.NEW);
            
            // Both orders should remain in the book
            assertThat(orderBook.getOrder(buyOrder.orderId())).isPresent();
            assertThat(orderBook.getOrder(sellOrder.orderId())).isPresent();
        }

        @Test
        @DisplayName("should skip self-trades and match with others")
        void shouldSkipSelfTradesAndMatchOthers() {
            // Given
            Order buy1 = createLimitOrder(Side.BUY, "100.00", "5", "TRADER-A");
            Order buy2 = createLimitOrder(Side.BUY, "100.00", "5", "TRADER-B");
            Order buy3 = createLimitOrder(Side.BUY, "100.00", "5", "TRADER-A");

            matchingEngine.processOrder(buy1);
            matchingEngine.processOrder(buy2);
            matchingEngine.processOrder(buy3);

            // When - TRADER-A sells
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "10", "TRADER-A");
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then - should only match with TRADER-B
            assertThat(result.trades()).hasSize(1);
            assertThat(result.trades().getFirst().buyer()).isEqualTo("TRADER-B");
            assertThat(result.isPartiallyFilled()).isTrue();
            assertThat(result.filledQuantity()).isEqualByComparingTo("5");
        }
    }

    @Nested
    @DisplayName("Partial Fill Scenarios")
    class PartialFillScenarios {

        @Test
        @DisplayName("should partially fill large order against small order")
        void shouldPartiallyFillLargeOrder() {
            // Given - small buy order
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "5");
            matchingEngine.processOrder(buyOrder);

            // When - large sell order
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "20");
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then
            assertThat(result.isPartiallyFilled()).isTrue();
            assertThat(result.filledQuantity()).isEqualByComparingTo("5");
            assertThat(result.remainingOrder()).isPresent();
            assertThat(result.remainingOrder().get().quantity()).isEqualByComparingTo("15");
            
            // Sell order should be in book with remaining quantity
            assertThat(orderBook.getOrder(sellOrder.orderId())).isPresent();
        }

        @Test
        @DisplayName("should fill large order against multiple small orders")
        void shouldFillLargeOrderAgainstMultiple() {
            // Given - multiple small buy orders
            Order buy1 = createLimitOrder(Side.BUY, "100.00", "5", "TRADER-A");
            Order buy2 = createLimitOrder(Side.BUY, "100.00", "3", "TRADER-B");
            Order buy3 = createLimitOrder(Side.BUY, "100.00", "7", "TRADER-C");

            matchingEngine.processOrder(buy1);
            matchingEngine.processOrder(buy2);
            matchingEngine.processOrder(buy3);

            // When - large sell order
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "12", "TRADER-D");
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then
            assertThat(result.isFullyFilled()).isTrue();
            assertThat(result.trades()).hasSize(3);
            assertThat(result.filledQuantity()).isEqualByComparingTo("12");
            
            // The first two orders fully filled, the third partially
            assertThat(result.trades().get(0).quantity()).isEqualByComparingTo("5");
            assertThat(result.trades().get(1).quantity()).isEqualByComparingTo("3");
            assertThat(result.trades().get(2).quantity()).isEqualByComparingTo("4");
            
            // Check remaining orders in a book
            assertThat(orderBook.getOrder(buy1.orderId())).isEmpty();
            assertThat(orderBook.getOrder(buy2.orderId())).isEmpty();
            assertThat(orderBook.getOrder(buy3.orderId())).isPresent();
        }

        @Test
        @DisplayName("should correctly track remaining quantities")
        void shouldTrackRemainingQuantities() {
            // Given
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "20", "TRADER-A");
            matchingEngine.processOrder(buyOrder);

            // When - multiple partial fills
            Order sell1 = createLimitOrder(Side.SELL, "100.00", "5", "TRADER-B");
            Order sell2 = createLimitOrder(Side.SELL, "100.00", "7", "TRADER-C");
            Order sell3 = createLimitOrder(Side.SELL, "100.00", "8", "TRADER-D");

            MatchResult result1 = matchingEngine.processOrder(sell1);
            MatchResult result2 = matchingEngine.processOrder(sell2);
            MatchResult result3 = matchingEngine.processOrder(sell3);

            // Then
            assertThat(result1.isFullyFilled()).isTrue();
            assertThat(result2.isFullyFilled()).isTrue();
            assertThat(result3.isFullyFilled()).isTrue();
            
            // Buy order should be fully filled
            assertThat(orderBook.getOrder(buyOrder.orderId())).isEmpty();
            assertThat(matchingEngine.getOrderStatus(buyOrder.orderId())).isEqualTo(OrderStatus.FILLED);
        }
    }

    @Nested
    @DisplayName("Market Order Handling")
    class MarketOrderHandling {

        @Test
        @DisplayName("should match market buy order at best ask")
        void shouldMatchMarketBuyAtBestAsk() {
            // Given - multiple sell orders
            Order sell1 = createLimitOrder(Side.SELL, "101.00", "5");
            Order sell2 = createLimitOrder(Side.SELL, "100.00", "5");
            Order sell3 = createLimitOrder(Side.SELL, "102.00", "5");

            matchingEngine.processOrder(sell1);
            matchingEngine.processOrder(sell2);
            matchingEngine.processOrder(sell3);

            // When - market buy order
            Order marketBuy = createMarketOrder(Side.BUY, "10");
            MatchResult result = matchingEngine.processOrder(marketBuy);

            // Then - should match at best prices: 100, then 101
            assertThat(result.isFullyFilled()).isTrue();
            assertThat(result.trades()).hasSize(2);
            assertThat(result.trades().get(0).price()).isEqualByComparingTo("100.00");
            assertThat(result.trades().get(1).price()).isEqualByComparingTo("101.00");
            assertThat(result.averagePrice()).isEqualByComparingTo("100.50");
        }

        @Test
        @DisplayName("should match market sell order at best bid")
        void shouldMatchMarketSellAtBestBid() {
            // Given - multiple buy orders
            Order buy1 = createLimitOrder(Side.BUY, "99.00", "5");
            Order buy2 = createLimitOrder(Side.BUY, "100.00", "5");
            Order buy3 = createLimitOrder(Side.BUY, "98.00", "5");

            matchingEngine.processOrder(buy1);
            matchingEngine.processOrder(buy2);
            matchingEngine.processOrder(buy3);

            // When - market sell order
            Order marketSell = createMarketOrder(Side.SELL, "10");
            MatchResult result = matchingEngine.processOrder(marketSell);

            // Then - should match at best prices: 100, then 99
            assertThat(result.isFullyFilled()).isTrue();
            assertThat(result.trades()).hasSize(2);
            assertThat(result.trades().get(0).price()).isEqualByComparingTo("100.00");
            assertThat(result.trades().get(1).price()).isEqualByComparingTo("99.00");
        }

        @Test
        @DisplayName("should handle market order with no liquidity")
        void shouldHandleMarketOrderNoLiquidity() {
            // Given - empty book
            
            // When - market order
            Order marketOrder = createMarketOrder(Side.BUY, "10");
            MatchResult result = matchingEngine.processOrder(marketOrder);

            // Then - no match, but market orders can't be added to book (no price)
            assertThat(result.hasExecutions()).isFalse();
            assertThat(result.finalStatus()).isEqualTo(OrderStatus.NEW);
            assertThat(result.remainingOrder()).isPresent();
            
            // Market order should NOT be in book (null price)
            assertThat(orderBook.getOrder(marketOrder.orderId())).isEmpty();
        }

    }

    @Nested
    @DisplayName("Order Status Management")
    class OrderStatusManagement {

        @Test
        @DisplayName("should track NEW status for unmatched order")
        void shouldTrackNewStatus() {
            // Given/When
            Order order = createLimitOrder(Side.BUY, "100.00", "10");
            matchingEngine.processOrder(order);

            // Then
            assertThat(matchingEngine.getOrderStatus(order.orderId())).isEqualTo(OrderStatus.NEW);
        }

        @Test
        @DisplayName("should track FILLED status")
        void shouldTrackFilledStatus() {
            // Given
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "10");
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "10");

            matchingEngine.processOrder(buyOrder);
            matchingEngine.processOrder(sellOrder);

            // Then
            assertThat(matchingEngine.getOrderStatus(buyOrder.orderId())).isEqualTo(OrderStatus.FILLED);
            assertThat(matchingEngine.getOrderStatus(sellOrder.orderId())).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        @DisplayName("should track PARTIALLY_FILLED status")
        void shouldTrackPartiallyFilledStatus() {
            // Given
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "10");
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "5");

            matchingEngine.processOrder(buyOrder);
            matchingEngine.processOrder(sellOrder);

            // Then
            assertThat(matchingEngine.getOrderStatus(buyOrder.orderId())).isEqualTo(OrderStatus.PARTIALLY_FILLED);
            assertThat(matchingEngine.getOrderStatus(sellOrder.orderId())).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        @DisplayName("should cancel order successfully")
        void shouldCancelOrder() {
            // Given
            Order order = createLimitOrder(Side.BUY, "100.00", "10");
            matchingEngine.processOrder(order);

            // When
            boolean cancelled = matchingEngine.cancelOrder(order.orderId());

            // Then
            assertThat(cancelled).isTrue();
            assertThat(matchingEngine.getOrderStatus(order.orderId())).isEqualTo(OrderStatus.CANCELLED);
            assertThat(orderBook.getOrder(order.orderId())).isEmpty();
        }

        @Test
        @DisplayName("should handle cancelling partially filled order")
        void shouldCancelPartiallyFilledOrder() {
            // Given
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "10");
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "3");

            matchingEngine.processOrder(buyOrder);
            matchingEngine.processOrder(sellOrder);

            // When
            boolean cancelled = matchingEngine.cancelOrder(buyOrder.orderId());

            // Then
            assertThat(cancelled).isTrue();
            assertThat(matchingEngine.getOrderStatus(buyOrder.orderId())).isEqualTo(OrderStatus.CANCELLED);
            assertThat(orderBook.getOrder(buyOrder.orderId())).isEmpty();
        }

        @Test
        @DisplayName("should return NEW status for unknown order")
        void shouldReturnNewStatusForUnknownOrder() {
            assertThat(matchingEngine.getOrderStatus("UNKNOWN-ID")).isEqualTo(OrderStatus.NEW);
        }
    }

    @Nested
    @DisplayName("Validation Edge Cases")
    class ValidationEdgeCases {

        @ParameterizedTest(name = "should accept valid {0} order")
        @CsvSource({
            "minimum price, 0.01, 10.00",
            "maximum price, 1000000.00, 10.00",
            "minimum quantity, 100.00, 0.01",
            "maximum quantity, 100.00, 1000000.00"
        })
        @DisplayName("should accept orders at valid boundaries")
        void shouldAcceptValidBoundaryOrders(String description, String price, String quantity) {
            // Given/When
            Order order = createLimitOrder(Side.BUY, price, quantity);
            MatchResult result = matchingEngine.processOrder(order);

            // Then
            assertThat(result).isNotNull();
            assertThat(orderBook.getOrder(order.orderId())).isPresent();
        }

        @ParameterizedTest(name = "should reject {0}")
        @CsvSource({
            "order below minimum price, 0.001, 10.00, below minimum allowed price",
            "order above maximum price, 1000001.00, 10.00, exceeds maximum allowed price",
            "order below minimum quantity, 100.00, 0.001, below minimum allowed quantity",
            "order above maximum quantity, 100.00, 1000001.00, exceeds maximum allowed quantity"
        })
        @DisplayName("should reject orders outside valid boundaries")
        void shouldRejectInvalidBoundaryOrders(String description, String price, String quantity, String expectedMessage) {
            // Given
            Order order = new Order(
                    UUID.randomUUID().toString(),
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal(price),
                    new BigDecimal(quantity),
                    "TRADER-1",
                    Instant.now()
            );

            // When/Then
            assertThatThrownBy(() -> matchingEngine.processOrder(order))
                    .isInstanceOf(OrderValidator.ValidationException.class)
                    .hasMessageContaining(expectedMessage);
        }
    }

    @Nested
    @DisplayName("Trader Volume Tracking")
    class TraderVolumeTracking {

        @Test
        @DisplayName("should track volume for new trader")
        void shouldTrackVolumeForNewTrader() {
            // Given - no prior trades
            assertThat(matchingEngine.getTradedVolume("TRADER-NEW")).isEqualByComparingTo(BigDecimal.ZERO);

            // When - execute trade
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "10", "TRADER-NEW");
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "10", "TRADER-OTHER");
            
            matchingEngine.processOrder(buyOrder);
            matchingEngine.processOrder(sellOrder);

            // Then
            assertThat(matchingEngine.getTradedVolume("TRADER-NEW")).isEqualByComparingTo("1000.00");
            assertThat(matchingEngine.getTradedVolume("TRADER-OTHER")).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("should accumulate volume across multiple trades")
        void shouldAccumulateVolume() {
            // Given/When - multiple trades
            Order buy1 = createLimitOrder(Side.BUY, "100.00", "5", "TRADER-A");
            Order buy2 = createLimitOrder(Side.BUY, "101.00", "3", "TRADER-A");
            Order buy3 = createLimitOrder(Side.BUY, "99.00", "3", "TRADER-A");
            Order sell1 = createLimitOrder(Side.SELL, "100.00", "5", "TRADER-B");
            Order sell2 = createLimitOrder(Side.SELL, "99.00", "3", "TRADER-B");

            matchingEngine.processOrder(buy1);
            matchingEngine.processOrder(buy2);
            matchingEngine.processOrder(buy3);
            matchingEngine.processOrder(sell1);
            matchingEngine.processOrder(sell2);

            // Then
            // TRADER-A: buy2 matches sell2 at 101 (3 * 101 = 303) + buy1 matches sell1 at 100 (5 * 100 = 500) = 803
            // TRADER-B: same trades from the other side
            assertThat(matchingEngine.getTradedVolume("TRADER-A")).isEqualByComparingTo("803.00");
            assertThat(matchingEngine.getTradedVolume("TRADER-B")).isEqualByComparingTo("803.00");
        }

        @Test
        @DisplayName("should track volume for partial fills")
        void shouldTrackVolumeForPartialFills() {
            // Given
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "20", "TRADER-A");
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "7", "TRADER-B");

            matchingEngine.processOrder(buyOrder);
            matchingEngine.processOrder(sellOrder);

            // Then - both traders should have volume from partial fill
            assertThat(matchingEngine.getTradedVolume("TRADER-A")).isEqualByComparingTo("700.00");
            assertThat(matchingEngine.getTradedVolume("TRADER-B")).isEqualByComparingTo("700.00");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should reject null order")
        void shouldRejectNullOrder() {
            assertThatThrownBy(() -> matchingEngine.processOrder(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Incoming order cannot be null");
        }

        @Test
        @DisplayName("should handle null order ID for status check")
        void shouldHandleNullOrderIdForStatus() {
            assertThatThrownBy(() -> matchingEngine.getOrderStatus(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Order ID cannot be null");
        }

        @Test
        @DisplayName("should handle null order ID for cancellation")
        void shouldHandleNullOrderIdForCancellation() {
            assertThatThrownBy(() -> matchingEngine.cancelOrder(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Order ID cannot be null");
        }

        @Test
        @DisplayName("should handle null trader ID for volume check")
        void shouldHandleNullTraderIdForVolume() {
            assertThatThrownBy(() -> matchingEngine.getTradedVolume(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Trader ID cannot be null");
        }

        @Test
        @DisplayName("should return false for cancelling non-existent order")
        void shouldReturnFalseForCancellingNonExistent() {
            boolean result = matchingEngine.cancelOrder("NON-EXISTENT-ID");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return zero volume for unknown trader")
        void shouldReturnZeroVolumeForUnknownTrader() {
            BigDecimal volume = matchingEngine.getTradedVolume("UNKNOWN-TRADER");
            assertThat(volume).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("MatchResult Validation")
    class MatchResultValidation {

        @Test
        @DisplayName("should calculate correct average price for single trade")
        void shouldCalculateAveragePriceSingleTrade() {
            // Given
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "10");
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "10");

            matchingEngine.processOrder(buyOrder);
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then
            assertThat(result.averagePrice()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("should calculate correct average price for multiple trades")
        void shouldCalculateAveragePriceMultipleTrades() {
            // Given - orders at different prices
            Order buy1 = createLimitOrder(Side.BUY, "101.00", "5");
            Order buy2 = createLimitOrder(Side.BUY, "100.00", "10");
            Order buy3 = createLimitOrder(Side.BUY, "99.00", "5");

            matchingEngine.processOrder(buy1);
            matchingEngine.processOrder(buy2);
            matchingEngine.processOrder(buy3);

            // When - market sell that hits all levels
            Order sellOrder = createMarketOrder(Side.SELL, "20");
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then - weighted average: (5*101 + 10*100 + 5*99) / 20 = 100
            assertThat(result.averagePrice()).isEqualByComparingTo("100.00");
            assertThat(result.filledQuantity()).isEqualByComparingTo("20");
        }

        @Test
        @DisplayName("should have consistent trade count and filled quantity")
        void shouldHaveConsistentTradeData() {
            // Given
            Order buy1 = createLimitOrder(Side.BUY, "100.00", "3");
            Order buy2 = createLimitOrder(Side.BUY, "100.00", "4");
            Order buy3 = createLimitOrder(Side.BUY, "100.00", "3");

            matchingEngine.processOrder(buy1);
            matchingEngine.processOrder(buy2);
            matchingEngine.processOrder(buy3);

            // When
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "10");
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then
            assertThat(result.getTradeCount()).isEqualTo(3);
            assertThat(result.trades()).hasSize(3);
            
            BigDecimal totalTradeQuantity = result.trades().stream()
                    .map(Trade::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(totalTradeQuantity).isEqualByComparingTo(result.filledQuantity());
        }
    }

    @Nested
    @DisplayName("Special Edge Cases")
    class SpecialEdgeCases {

        @Test
        @DisplayName("should handle decimal precision correctly")
        void shouldHandleDecimalPrecision() {
            // Given - prices and quantities with decimals
            Order buyOrder = createLimitOrder(Side.BUY, "100.50", "10.25");
            Order sellOrder = createLimitOrder(Side.SELL, "100.50", "10.25");

            matchingEngine.processOrder(buyOrder);
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then
            assertThat(result.trades().getFirst().price()).isEqualByComparingTo("100.50");
            assertThat(result.trades().getFirst().quantity()).isEqualByComparingTo("10.25");
            assertThat(result.averagePrice()).isEqualByComparingTo("100.50");
        }

        @Test
        @DisplayName("should preserve order ID in remaining order after partial fill")
        void shouldPreserveOrderIdInRemainingOrder() {
            // Given
            Order buyOrder = createLimitOrder(Side.BUY, "100.00", "20");
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "5");

            matchingEngine.processOrder(buyOrder);
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then - buy order should still be in book with same ID
            assertThat(orderBook.getOrder(buyOrder.orderId())).isPresent();
            Order remainingInBook = orderBook.getOrder(buyOrder.orderId()).get();
            assertThat(remainingInBook.orderId()).isEqualTo(buyOrder.orderId());
            assertThat(remainingInBook.quantity()).isEqualByComparingTo("15");
        }

        @Test
        @DisplayName("should handle orders with same timestamp")
        void shouldHandleOrdersWithSameTimestamp() {
            // Given - create orders with the exact same timestamp
            Instant now = Instant.now();
            Order buy1 = new Order("ORDER-1", OrderType.LIMIT, Side.BUY, 
                    new BigDecimal("100.00"), new BigDecimal("5"), "TRADER-A", now);
            Order buy2 = new Order("ORDER-2", OrderType.LIMIT, Side.BUY, 
                    new BigDecimal("100.00"), new BigDecimal("5"), "TRADER-B", now);

            matchingEngine.processOrder(buy1);
            matchingEngine.processOrder(buy2);

            // When
            Order sellOrder = createLimitOrder(Side.SELL, "100.00", "10");
            MatchResult result = matchingEngine.processOrder(sellOrder);

            // Then - should still maintain order based on a processing sequence
            assertThat(result.trades()).hasSize(2);
            assertThat(result.trades().get(0).buyer()).isEqualTo("TRADER-A");
            assertThat(result.trades().get(1).buyer()).isEqualTo("TRADER-B");
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("should handle concurrent order processing")
        void shouldHandleConcurrentOrderProcessing() throws InterruptedException, ExecutionException {
            // Given
            int threadCount = 10;
            int ordersPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Future<List<MatchResult>>> futures = new ArrayList<>();

            // When - submit orders concurrently
            try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
                for (int i = 0; i < threadCount; i++) {
                    final int threadId = i;
                    Future<List<MatchResult>> future = executor.submit(() -> {
                        List<MatchResult> results = new ArrayList<>();
                        try {
                            for (int j = 0; j < ordersPerThread; j++) {
                                Side side = (threadId + j) % 2 == 0 ? Side.BUY : Side.SELL;
                                Order order = createLimitOrder(side, "100.00", "1", 
                                        "TRADER-" + threadId + "-" + j);
                                results.add(matchingEngine.processOrder(order));
                            }
                        } finally {
                            latch.countDown();
                        }
                        return results;
                    });
                    futures.add(future);
                }

                // Wait for completion
                assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
                executor.shutdown();

                // Then - verify all orders were processed
                int totalTrades = 0;
                for (Future<List<MatchResult>> future : futures) {
                    List<MatchResult> results = future.get();
                    assertThat(results).hasSize(ordersPerThread);
                    totalTrades += results.stream()
                            .mapToInt(MatchResult::getTradeCount)
                            .sum();
                }

                // Should have many trades from matching
                assertThat(totalTrades).isGreaterThan(0);
            }
        }

        @Test
        @DisplayName("should handle concurrent cancellations")
        void shouldHandleConcurrentCancellations() throws InterruptedException, ExecutionException {
            // Given - add orders
            List<String> orderIds = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Order order = createLimitOrder(Side.BUY, "100.00", "10", "TRADER-" + i);
                matchingEngine.processOrder(order);
                orderIds.add(order.orderId());
            }

            // When - cancel concurrently
            CountDownLatch latch = new CountDownLatch(orderIds.size());
            List<Future<Boolean>> futures = new ArrayList<>();

            try (ExecutorService executor = Executors.newFixedThreadPool(10)) {
                for (String orderId : orderIds) {
                    Future<Boolean> future = executor.submit(() -> {
                        try {
                            return matchingEngine.cancelOrder(orderId);
                        } finally {
                            latch.countDown();
                        }
                    });
                    futures.add(future);
                }

                // Wait and verify
                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                executor.shutdown();

                // All cancellations should succeed
                for (Future<Boolean> future : futures) {
                    assertThat(future.get()).isTrue();
                }

                // Order book should be empty
                assertThat(orderBook.isEmpty()).isTrue();
            }
        }
    }

    // Helper methods
    
    private Order createLimitOrder(Side side, String price, String quantity) {
        return createLimitOrder(side, price, quantity, side == Side.BUY ? "TRADER-1" : "TRADER-2");
    }

    private Order createLimitOrder(Side side, String price, String quantity, String traderId) {
        return new Order(
                UUID.randomUUID().toString(),
                OrderType.LIMIT,
                side,
                new BigDecimal(price),
                new BigDecimal(quantity),
                traderId,
                Instant.now()
        );
    }

    private Order createMarketOrder(Side side, String quantity) {
        return createMarketOrder(side, quantity, side == Side.BUY ? "TRADER-1" : "TRADER-2");
    }

    private Order createMarketOrder(Side side, String quantity, String traderId) {
        return new Order(
                UUID.randomUUID().toString(),
                OrderType.MARKET,
                side,
                null,
                new BigDecimal(quantity),
                traderId,
                Instant.now()
        );
    }
}
