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
            assertThat(orderBook.isEmpty()).isTrue();
            assertThat(orderBook.getTotalOrderCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Adding Orders")
    class AddingOrders {

        @Test
        @DisplayName("should add buy order and track quantity correctly")
        void shouldAddBuyOrderAndTrackQuantity() {
            // Given
            Order buyOrder = createOrder(Side.BUY, "100.50", "1000");

            // When
            orderBook.addOrder(buyOrder);

            // Then
            assertThat(orderBook.getBestBid()).hasValue(new BigDecimal("100.50"));
            assertThat(orderBook.getOrder(buyOrder.orderId())).hasValue(buyOrder);
            assertThat(orderBook.getMarketDepth(Side.BUY)).isEqualTo(1);
            assertThat(orderBook.getQuantityAtPrice(Side.BUY, new BigDecimal("100.50")))
                    .isEqualByComparingTo("1000");
            assertThat(orderBook.getPriceLevelCount(Side.BUY)).isEqualTo(1);
        }

        @Test
        @DisplayName("should accumulate quantities at same price level")
        void shouldAccumulateQuantitiesAtSamePrice() {
            // Given
            Order order1 = createOrder(Side.BUY, "100.00", "500");
            Order order2 = createOrder(Side.BUY, "100.00", "300");
            Order order3 = createOrder(Side.BUY, "100.00", "200");

            // When
            orderBook.addOrder(order1);
            orderBook.addOrder(order2);
            orderBook.addOrder(order3);

            // Then
            assertThat(orderBook.getQuantityAtPrice(Side.BUY, new BigDecimal("100.00")))
                    .isEqualByComparingTo("1000");
            assertThat(orderBook.getMarketDepth(Side.BUY)).isEqualTo(3);
            assertThat(orderBook.getPriceLevelCount(Side.BUY)).isEqualTo(1);
        }

        @Test
        @DisplayName("should maintain price priority across multiple levels")
        void shouldMaintainPricePriorityAcrossLevels() {
            // Given
            List<Order> orders = List.of(
                    createOrder(Side.BUY, "99.50", "100"),
                    createOrder(Side.BUY, "100.00", "200"),
                    createOrder(Side.BUY, "100.50", "300"),
                    createOrder(Side.SELL, "101.00", "150"),
                    createOrder(Side.SELL, "100.75", "250"),
                    createOrder(Side.SELL, "101.25", "350")
            );

            // When
            orders.forEach(orderBook::addOrder);

            // Then
            assertThat(orderBook.getBestBid()).hasValue(new BigDecimal("100.50"));
            assertThat(orderBook.getBestAsk()).hasValue(new BigDecimal("100.75"));
            assertThat(orderBook.getSpread()).hasValue(new BigDecimal("0.25"));

            // Check price level ordering
            var topBidLevels = orderBook.getTopPriceLevels(Side.BUY, 3);
            assertThat(topBidLevels)
                    .hasSize(3)
                    .extracting(PriceLevel::getPrice)
                    .containsExactly(
                            new BigDecimal("100.50"),
                            new BigDecimal("100.00"),
                            new BigDecimal("99.50")
                    );
        }

        @Test
        @DisplayName("should reject duplicate order IDs")
        void shouldRejectDuplicateOrderIds() {
            // Given
            Order order1 = createOrderWithId("DUPLICATE-ID", Side.BUY, "100.00", "1000");
            Order order2 = createOrderWithId("DUPLICATE-ID", Side.SELL, "101.00", "500");

            orderBook.addOrder(order1);

            // When/Then
            assertThatThrownBy(() -> orderBook.addOrder(order2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("should reject invalid orders")
        void shouldRejectInvalidOrders() {
            assertThatThrownBy(() -> orderBook.addOrder(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Order cannot be null");

            Order zeroQuantityOrder = createOrder(Side.BUY, "100.00", "0");
            assertThatThrownBy(() -> orderBook.addOrder(zeroQuantityOrder))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Order quantity must be positive");
        }
    }

    @Nested
    @DisplayName("Removing Orders with PriceLevel Cleanup")
    class RemovingOrders {

        @Test
        @DisplayName("should remove order and update quantities")
        void shouldRemoveOrderAndUpdateQuantities() {
            // Given
            Order order1 = createOrder(Side.BUY, "100.00", "500");
            Order order2 = createOrder(Side.BUY, "100.00", "300");
            orderBook.addOrder(order1);
            orderBook.addOrder(order2);

            // When
            orderBook.removeOrder(order1.orderId());

            // Then
            assertThat(orderBook.getOrder(order1.orderId())).isEmpty();
            assertThat(orderBook.getOrder(order2.orderId())).hasValue(order2);
            assertThat(orderBook.getQuantityAtPrice(Side.BUY, new BigDecimal("100.00")))
                    .isEqualByComparingTo("300");
            assertThat(orderBook.getMarketDepth(Side.BUY)).isEqualTo(1);
        }

        @Test
        @DisplayName("should remove empty price levels")
        void shouldRemoveEmptyPriceLevels() {
            // Given
            Order singleOrder = createOrder(Side.BUY, "100.00", "1000");
            orderBook.addOrder(singleOrder);
            assertThat(orderBook.getPriceLevelCount(Side.BUY)).isEqualTo(1);

            // When
            orderBook.removeOrder(singleOrder.orderId());

            // Then
            assertThat(orderBook.getBestBid()).isEmpty();
            assertThat(orderBook.getPriceLevelCount(Side.BUY)).isZero();
            assertThat(orderBook.getQuantityAtPrice(Side.BUY, new BigDecimal("100.00")))
                    .isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("should handle removing non-existent order gracefully")
        void shouldHandleRemovingNonExistentOrder() {
            // When/Then - should not throw
            assertThatCode(() -> orderBook.removeOrder("NON-EXISTENT"))
                    .doesNotThrowAnyException();

            assertThatCode(() -> orderBook.removeOrder(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Enhanced Order Book Analytics")
    class Analytics {

        @Test
        @DisplayName("should provide accurate market depth analytics")
        void shouldProvideAccurateMarketDepthAnalytics() {
            // Given - create realistic order book
            List<Order> orders = List.of(
                    createOrder(Side.BUY, "100.00", "1000"),
                    createOrder(Side.BUY, "100.00", "500"),
                    createOrder(Side.BUY, "99.95", "2000"),
                    createOrder(Side.BUY, "99.90", "1500"),
                    createOrder(Side.SELL, "100.05", "800"),
                    createOrder(Side.SELL, "100.05", "1200"),
                    createOrder(Side.SELL, "100.10", "1000")
            );

            // When
            orders.forEach(orderBook::addOrder);

            // Then
            assertThat(orderBook.getMarketDepth(Side.BUY)).isEqualTo(4);
            assertThat(orderBook.getMarketDepth(Side.SELL)).isEqualTo(3);
            assertThat(orderBook.getMarketDepth(Side.BUY, 2)).isEqualTo(3); // Top 2 levels: 2 + 1
            assertThat(orderBook.getTotalOrderCount()).isEqualTo(7);

            // Verify top levels
            var topBidLevels = orderBook.getTopPriceLevels(Side.BUY, 2);
            assertThat(topBidLevels).hasSize(2);
            assertThat(topBidLevels.get(0).getOrderCount()).isEqualTo(2); // 100.00 level
            assertThat(topBidLevels.get(0).getTotalQuantity()).isEqualByComparingTo("1500");
        }

        @Test
        @DisplayName("should calculate spreads accurately")
        void shouldCalculateSpreadAccurately() {
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
        @DisplayName("should handle edge cases for analytics")
        void shouldHandleEdgeCasesForAnalytics() {
            // Empty book
            assertThat(orderBook.getTopPriceLevels(Side.BUY, 5)).isEmpty();
            assertThat(orderBook.getMarketDepth(Side.BUY, -1)).isZero();

            // Single side only
            orderBook.addOrder(createOrder(Side.BUY, "100.00", "1000"));
            assertThat(orderBook.getSpread()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Order Book Operations")
    class Operations {

        @Test
        @DisplayName("should clear all orders")
        void shouldClearAllOrders() {
            // Given
            orderBook.addOrder(createOrder(Side.BUY, "100.00", "1000"));
            orderBook.addOrder(createOrder(Side.SELL, "100.05", "500"));
            assertThat(orderBook.getTotalOrderCount()).isEqualTo(2);

            // When
            orderBook.clear();

            // Then
            assertThat(orderBook.isEmpty()).isTrue();
            assertThat(orderBook.getTotalOrderCount()).isZero();
            assertThat(orderBook.getPriceLevelCount(Side.BUY)).isZero();
            assertThat(orderBook.getPriceLevelCount(Side.SELL)).isZero();
        }

        @Test
        @DisplayName("should provide meaningful string representation")
        void shouldProvideStringRepresentation() {
            // Given
            orderBook.addOrder(createOrder(Side.BUY, "100.00", "1000"));
            orderBook.addOrder(createOrder(Side.BUY, "99.95", "500"));
            orderBook.addOrder(createOrder(Side.SELL, "100.05", "750"));

            // When
            String representation = orderBook.toString();

            // Then
            assertThat(representation)
                    .contains("bids=2 levels")
                    .contains("asks=1 levels")
                    .contains("total orders=3");
        }
    }

    @Nested
    @DisplayName("Thread Safety and Performance")
    class ThreadSafetyAndPerformance {

        @Test
        @DisplayName("should handle concurrent operations")
        void shouldHandleConcurrentOperations() {
            // This test would require more sophisticated setup for true concurrency testing
            // For now, just verify that operations don't interfere with each other

            // Given
            List<Order> orders = List.of(
                    createOrder(Side.BUY, "100.00", "100"),
                    createOrder(Side.BUY, "100.00", "200"),
                    createOrder(Side.SELL, "100.05", "150")
            );

            // When - simulate rapid operations
            orders.forEach(orderBook::addOrder);
            orderBook.removeOrder(orders.get(0).orderId());

            // Then - verify consistent state
            assertThat(orderBook.getMarketDepth(Side.BUY)).isEqualTo(1);
            assertThat(orderBook.getQuantityAtPrice(Side.BUY, new BigDecimal("100.00")))
                    .isEqualByComparingTo("200");
        }
    }

    @ParameterizedTest
    @DisplayName("should maintain order book integrity across operations")
    @CsvSource({
            "BUY,  100.00, 1000",
            "BUY,  99.95,  2000",
            "SELL, 100.05, 1500",
            "SELL, 100.10, 500"
    })
    void shouldMaintainIntegrityAcrossOperations(Side side, String price, String quantity) {
        // Given
        Order order = createOrder(side, price, quantity);

        // When - add then remove
        orderBook.addOrder(order);
        var depthAfterAdd = orderBook.getMarketDepth(side);
        var levelCountAfterAdd = orderBook.getPriceLevelCount(side);

        orderBook.removeOrder(order.orderId());
        var depthAfterRemove = orderBook.getMarketDepth(side);
        var levelCountAfterRemove = orderBook.getPriceLevelCount(side);

        // Then
        assertThat(depthAfterAdd).isEqualTo(1);
        assertThat(levelCountAfterAdd).isEqualTo(1);
        assertThat(depthAfterRemove).isZero();
        assertThat(levelCountAfterRemove).isZero();
    }

    private Order createOrder(Side side, String price, String quantity) {
        return createOrderWithId(UUID.randomUUID().toString(), side, price, quantity);
    }

    private Order createOrderWithId(String orderId, Side side, String price, String quantity) {
        return new Order(
                orderId,
                OrderType.LIMIT,
                side,
                new BigDecimal(price),
                new BigDecimal(quantity),
                "TEST-TRADER",
                Instant.now()
        );
    }
}