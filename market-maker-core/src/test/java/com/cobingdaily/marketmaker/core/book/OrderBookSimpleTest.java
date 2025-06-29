package com.cobingdaily.marketmaker.core.book;

import com.cobingdaily.marketmaker.core.model.Order;
import com.cobingdaily.marketmaker.core.model.OrderType;
import com.cobingdaily.marketmaker.core.model.Side;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderBookSimple Tests")
class OrderBookSimpleTest {

    private OrderBookSimple orderBook;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBookSimple();
    }

    @Nested
    @DisplayName("Order Book Initialization")
    class Initialization {

        @Test
        @DisplayName("should initialize with empty book")
        void shouldInitializeEmpty() {
            assertThat(orderBook.getBestBid()).isEmpty();
            assertThat(orderBook.getBestAsk()).isEmpty();
            assertThat(orderBook.getSpread()).isEmpty();
            assertThat(orderBook.getMarketDepth(Side.BUY)).isZero();
            assertThat(orderBook.getMarketDepth(Side.SELL)).isZero();
        }
    }

    @Nested
    @DisplayName("Adding Orders")
    class AddingOrders {

        @Test
        @DisplayName("should add buy order to bids")
        void shouldAddBuyOrderToBids() {
            // Given
            Order buyOrder = createOrder(Side.BUY, "100.50", "1000");

            // When
            orderBook.addOrder(buyOrder);

            // Then
            assertThat(orderBook.getBestBid()).hasValue(new BigDecimal("100.50"));
            assertThat(orderBook.getOrder(buyOrder.orderId())).hasValue(buyOrder);
            assertThat(orderBook.getMarketDepth(Side.BUY)).isEqualTo(1);
        }

        @Test
        @DisplayName("should add sell order to asks")
        void shouldAddSellOrderToAsks() {
            // Given
            Order sellOrder = createOrder(Side.SELL, "100.50", "1000");

            // When
            orderBook.addOrder(sellOrder);

            // Then
            assertThat(orderBook.getBestAsk()).hasValue(new BigDecimal("100.50"));
            assertThat(orderBook.getOrder(sellOrder.orderId())).hasValue(sellOrder);
            assertThat(orderBook.getMarketDepth(Side.SELL)).isEqualTo(1);
        }

        @Test
        @DisplayName("should maintain price priority for bids")
        void shouldMaintainPricePriorityForBids() {
            // Given
            Order lowBid = createOrder(Side.BUY, "99.50", "100");
            Order midBid = createOrder(Side.BUY, "100.00", "200");
            Order highBid = createOrder(Side.BUY, "100.50", "300");

            // When - add in random order
            orderBook.addOrder(midBid);
            orderBook.addOrder(lowBid);
            orderBook.addOrder(highBid);

            // Then - highest bid should be best
            assertThat(orderBook.getBestBid()).hasValue(new BigDecimal("100.50"));
        }

        @Test
        @DisplayName("should maintain price priority for asks")
        void shouldMaintainPricePriorityForAsks() {
            // Given
            Order lowAsk = createOrder(Side.SELL, "100.00", "100");
            Order midAsk = createOrder(Side.SELL, "100.50", "200");
            Order highAsk = createOrder(Side.SELL, "101.00", "300");

            // When - add in random order
            orderBook.addOrder(highAsk);
            orderBook.addOrder(lowAsk);
            orderBook.addOrder(midAsk);

            // Then - lowest ask should be best
            assertThat(orderBook.getBestAsk()).hasValue(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("should maintain time priority at same price")
        void shouldMaintainTimePriorityAtSamePrice() {
            // Given
            Order firstOrder = createOrder(Side.BUY, "100.00", "100");
            Order secondOrder = createOrder(Side.BUY, "100.00", "200");
            Order thirdOrder = createOrder(Side.BUY, "100.00", "300");

            // When
            orderBook.addOrder(firstOrder);
            orderBook.addOrder(secondOrder);
            orderBook.addOrder(thirdOrder);

            // Then
            var ordersAtPrice = orderBook.getOrdersAtPrice(Side.BUY, new BigDecimal("100.00"));
            assertThat(ordersAtPrice)
                    .hasSize(3)
                    .containsExactly(firstOrder, secondOrder, thirdOrder);
        }

        @Test
        @DisplayName("should reject null order")
        void shouldRejectNullOrder() {
            assertThatThrownBy(() -> orderBook.addOrder(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid Order");
        }
    }

    @Nested
    @DisplayName("Removing Orders")
    class RemovingOrders {

        @Test
        @DisplayName("should remove existing order")
        void shouldRemoveExistingOrder() {
            // Given
            Order order = createOrder(Side.BUY, "100.00", "1000");
            orderBook.addOrder(order);

            // When
            orderBook.removeOrder(order.orderId());

            // Then
            assertThat(orderBook.getOrder(order.orderId())).isEmpty();
            assertThat(orderBook.getMarketDepth(Side.BUY)).isZero();
        }

        @Test
        @DisplayName("should handle removing non-existent order")
        void shouldHandleRemovingNonExistentOrder() {
            // When/Then - should not throw
            assertThatCode(() -> orderBook.removeOrder("NON-EXISTENT"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should remove empty price levels")
        void shouldRemoveEmptyPriceLevels() {
            // Given
            Order singleOrder = createOrder(Side.BUY, "100.00", "1000");
            orderBook.addOrder(singleOrder);

            // When
            orderBook.removeOrder(singleOrder.orderId());

            // Then
            assertThat(orderBook.getBestBid()).isEmpty();
            assertThat(orderBook.getOrdersAtPrice(Side.BUY, new BigDecimal("100.00")))
                    .isNull();
        }

        @Test
        @DisplayName("should maintain other orders at same price level")
        void shouldMaintainOtherOrdersAtSamePrice() {
            // Given
            Order order1 = createOrder(Side.BUY, "100.00", "1000");
            Order order2 = createOrder(Side.BUY, "100.00", "2000");
            orderBook.addOrder(order1);
            orderBook.addOrder(order2);

            // When
            orderBook.removeOrder(order1.orderId());

            // Then
            assertThat(orderBook.getOrder(order2.orderId())).hasValue(order2);
            assertThat(orderBook.getBestBid()).hasValue(new BigDecimal("100.00"));
            assertThat(orderBook.getOrdersAtPrice(Side.BUY, new BigDecimal("100.00")))
                    .hasSize(1)
                    .contains(order2);
        }
    }

    @Nested
    @DisplayName("Best Bid/Ask and Spread")
    class BestPricesAndSpread {

        @Test
        @DisplayName("should calculate spread correctly")
        void shouldCalculateSpread() {
            // Given
            orderBook.addOrder(createOrder(Side.BUY, "99.95", "1000"));
            orderBook.addOrder(createOrder(Side.SELL, "100.05", "1000"));

            // When
            var spread = orderBook.getSpread();

            // Then
            assertThat(spread)
                    .hasValue(new BigDecimal("0.10"))
                    .hasValueSatisfying(s ->
                            assertThat(s).isEqualByComparingTo("0.10")
                    );
        }

        @Test
        @DisplayName("should return empty spread when no bids")
        void shouldReturnEmptySpreadWhenNoBids() {
            // Given
            orderBook.addOrder(createOrder(Side.SELL, "100.00", "1000"));

            // Then
            assertThat(orderBook.getSpread()).isEmpty();
        }

        @Test
        @DisplayName("should return empty spread when no asks")
        void shouldReturnEmptySpreadWhenNoAsks() {
            // Given
            orderBook.addOrder(createOrder(Side.BUY, "100.00", "1000"));

            // Then
            assertThat(orderBook.getSpread()).isEmpty();
        }

        @Test
        @DisplayName("should update best bid when higher bid added")
        void shouldUpdateBestBidWhenHigherBidAdded() {
            // Given
            orderBook.addOrder(createOrder(Side.BUY, "100.00", "1000"));

            // When
            orderBook.addOrder(createOrder(Side.BUY, "100.50", "500"));

            // Then
            assertThat(orderBook.getBestBid()).hasValue(new BigDecimal("100.50"));
        }

        @Test
        @DisplayName("should update best ask when lower ask added")
        void shouldUpdateBestAskWhenLowerAskAdded() {
            // Given
            orderBook.addOrder(createOrder(Side.SELL, "100.50", "1000"));

            // When
            orderBook.addOrder(createOrder(Side.SELL, "100.00", "500"));

            // Then
            assertThat(orderBook.getBestAsk()).hasValue(new BigDecimal("100.00"));
        }
    }

    @Nested
    @DisplayName("Market Depth")
    class MarketDepth {

        @Test
        @DisplayName("should calculate total market depth")
        void shouldCalculateTotalMarketDepth() {
            // Given
            orderBook.addOrder(createOrder(Side.BUY, "99.95", "100"));
            orderBook.addOrder(createOrder(Side.BUY, "99.90", "200"));
            orderBook.addOrder(createOrder(Side.BUY, "99.85", "300"));

            // Then
            assertThat(orderBook.getMarketDepth(Side.BUY)).isEqualTo(3);
        }

        @Test
        @DisplayName("should calculate market depth for specified levels")
        void shouldCalculateMarketDepthForLevels() {
            // Given - add orders at different price levels
            orderBook.addOrder(createOrder(Side.BUY, "100.00", "100"));
            orderBook.addOrder(createOrder(Side.BUY, "100.00", "200")); // Same level
            orderBook.addOrder(createOrder(Side.BUY, "99.95", "300"));
            orderBook.addOrder(createOrder(Side.BUY, "99.90", "400"));
            orderBook.addOrder(createOrder(Side.BUY, "99.85", "500"));

            // Then
            assertThat(orderBook.getMarketDepth(Side.BUY, 1)).isEqualTo(2);
            assertThat(orderBook.getMarketDepth(Side.BUY, 2)).isEqualTo(3);
            assertThat(orderBook.getMarketDepth(Side.BUY, 10)).isEqualTo(5);
        }

        @Test
        @DisplayName("should return zero depth for empty side")
        void shouldReturnZeroDepthForEmptySide() {
            // Given
            orderBook.addOrder(createOrder(Side.BUY, "100.00", "1000"));

            // Then
            assertThat(orderBook.getMarketDepth(Side.SELL)).isZero();
            assertThat(orderBook.getMarketDepth(Side.SELL, 5)).isZero();
        }
    }

    @Nested
    @DisplayName("Get Orders")
    class GetOrders {

        @Test
        @DisplayName("should get order by ID")
        void shouldGetOrderById() {
            // Given
            Order order = createOrder(Side.BUY, "100.00", "1000");
            orderBook.addOrder(order);

            // Then
            assertThat(orderBook.getOrder(order.orderId()))
                    .hasValue(order)
                    .hasValueSatisfying(o -> {
                        assertThat(o.price()).isEqualByComparingTo("100.00");
                        assertThat(o.quantity()).isEqualByComparingTo("1000");
                        assertThat(o.side()).isEqualTo(Side.BUY);
                    });
        }

        @Test
        @DisplayName("should return empty for non-existent order")
        void shouldReturnEmptyForNonExistentOrder() {
            assertThat(orderBook.getOrder("NON-EXISTENT")).isEmpty();
        }

        @Test
        @DisplayName("should get orders at specific price")
        void shouldGetOrdersAtPrice() {
            // Given
            Order order1 = createOrder(Side.BUY, "100.00", "1000");
            Order order2 = createOrder(Side.BUY, "100.00", "2000");
            Order order3 = createOrder(Side.BUY, "99.95", "3000");

            orderBook.addOrder(order1);
            orderBook.addOrder(order2);
            orderBook.addOrder(order3);

            // When
            var ordersAt100 = orderBook.getOrdersAtPrice(Side.BUY, new BigDecimal("100.00"));

            // Then
            assertThat(ordersAt100)
                    .isNotNull()
                    .hasSize(2)
                    .containsExactly(order1, order2);
        }

        @Test
        @DisplayName("should return null for non-existent price level")
        void shouldReturnNullForNonExistentPriceLevel() {
            assertThat(orderBook.getOrdersAtPrice(Side.BUY, new BigDecimal("100.00")))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("Complex Scenarios")
    class ComplexScenarios {

        @Test
        @DisplayName("should handle multiple orders across multiple price levels")
        void shouldHandleComplexOrderBook() {
            // Given - create a realistic order book
            List<Order> buyOrders = List.of(
                    createOrder(Side.BUY, "100.00", "1000"),
                    createOrder(Side.BUY, "100.00", "500"),
                    createOrder(Side.BUY, "99.95", "2000"),
                    createOrder(Side.BUY, "99.90", "1500")
            );

            List<Order> sellOrders = List.of(
                    createOrder(Side.SELL, "100.05", "800"),
                    createOrder(Side.SELL, "100.05", "1200"),
                    createOrder(Side.SELL, "100.10", "1000"),
                    createOrder(Side.SELL, "100.15", "500")
            );

            // When
            buyOrders.forEach(orderBook::addOrder);
            sellOrders.forEach(orderBook::addOrder);

            // Then
            assertThat(orderBook.getBestBid()).hasValue(new BigDecimal("100.00"));
            assertThat(orderBook.getBestAsk()).hasValue(new BigDecimal("100.05"));
            assertThat(orderBook.getSpread()).hasValue(new BigDecimal("0.05"));
            assertThat(orderBook.getMarketDepth(Side.BUY)).isEqualTo(4);
            assertThat(orderBook.getMarketDepth(Side.SELL)).isEqualTo(4);
        }

        @ParameterizedTest
        @DisplayName("should maintain order book integrity after various operations")
        @CsvSource({
                "BUY,  100.00, 1000",
                "BUY,  99.95,  2000",
                "SELL, 100.05, 1500",
                "SELL, 100.10, 500"
        })
        void shouldMaintainIntegrity(Side side, String price, String quantity) {
            // Given
            Order order = createOrder(side, price, quantity);

            // When
            orderBook.addOrder(order);

            // Then
            assertThat(orderBook.getOrder(order.orderId())).hasValue(order);
            assertThat(orderBook.getMarketDepth(side)).isPositive();

            // When
            orderBook.removeOrder(order.orderId());

            // Then
            assertThat(orderBook.getOrder(order.orderId())).isEmpty();
            assertThat(orderBook.getMarketDepth(side)).isZero();
        }
    }

    private Order createOrder(Side side, String price, String quantity) {
        return new Order(
                UUID.randomUUID().toString(),
                OrderType.LIMIT,
                side,
                new BigDecimal(price),
                new BigDecimal(quantity),
                "TEST-TRADER",
                Instant.now()
        );
    }
}