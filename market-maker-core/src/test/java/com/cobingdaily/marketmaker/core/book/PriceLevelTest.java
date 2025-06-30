package com.cobingdaily.marketmaker.core.book;

import com.cobingdaily.marketmaker.core.model.Order;
import com.cobingdaily.marketmaker.core.model.OrderType;
import com.cobingdaily.marketmaker.core.model.Side;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PriceLevel Tests")
class PriceLevelTest {

    private PriceLevel priceLevel;
    private final BigDecimal testPrice = new BigDecimal("100.00");

    @BeforeEach
    void setUp() {
        priceLevel = new PriceLevel(testPrice);
    }

    @Nested
    @DisplayName("PriceLevel Initialization")
    class Initialization {

        @Test
        @DisplayName("should initialize empty price level")
        void shouldInitializeEmpty() {
            assertThat(priceLevel.getPrice()).isEqualByComparingTo(testPrice);
            assertThat(priceLevel.isEmpty()).isTrue();
            assertThat(priceLevel.getOrderCount()).isZero();
            assertThat(priceLevel.getTotalQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(priceLevel.peekFirst()).isNull();
        }

        @Test
        @DisplayName("should reject null price")
        void shouldRejectNullPrice() {
            assertThatThrownBy(() -> new PriceLevel(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Price cannot be null");
        }
    }

    @Nested
    @DisplayName("Adding Orders")
    class AddingOrders {

        @Test
        @DisplayName("should add order and update metrics")
        void shouldAddOrderAndUpdateMetrics() {
            // Given
            Order order = createOrder("1000");

            // When
            priceLevel.addOrder(order);

            // Then
            assertThat(priceLevel.isEmpty()).isFalse();
            assertThat(priceLevel.getOrderCount()).isEqualTo(1);
            assertThat(priceLevel.getTotalQuantity()).isEqualByComparingTo("1000");
            assertThat(priceLevel.peekFirst()).isEqualTo(order);
        }

        @Test
        @DisplayName("should accumulate quantities correctly")
        void shouldAccumulateQuantities() {
            // Given
            Order order1 = createOrder("500");
            Order order2 = createOrder("300");
            Order order3 = createOrder("200");

            // When
            priceLevel.addOrder(order1);
            priceLevel.addOrder(order2);
            priceLevel.addOrder(order3);

            // Then
            assertThat(priceLevel.getOrderCount()).isEqualTo(3);
            assertThat(priceLevel.getTotalQuantity()).isEqualByComparingTo("1000");
            assertThat(priceLevel.peekFirst()).isEqualTo(order1); // First in, first out
        }

        @Test
        @DisplayName("should maintain time priority (FIFO)")
        void shouldMaintainTimePriority() {
            // Given
            Order firstOrder = createOrder("100");
            Order secondOrder = createOrder("200");
            Order thirdOrder = createOrder("300");

            // When
            priceLevel.addOrder(firstOrder);
            priceLevel.addOrder(secondOrder);
            priceLevel.addOrder(thirdOrder);

            // Then
            var orders = priceLevel.getOrders();
            assertThat(orders)
                    .hasSize(3)
                    .containsExactly(firstOrder, secondOrder, thirdOrder);
        }

        @Test
        @DisplayName("should reject null order")
        void shouldRejectNullOrder() {
            assertThatThrownBy(() -> priceLevel.addOrder(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Order cannot be null");
        }

        @Test
        @DisplayName("should reject order with wrong price")
        void shouldRejectOrderWithWrongPrice() {
            // Given
            Order wrongPriceOrder = new Order(
                    UUID.randomUUID().toString(),
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("99.99"), // Wrong price
                    new BigDecimal("1000"),
                    "TRADER-1",
                    Instant.now()
            );

            // When/Then
            assertThatThrownBy(() -> priceLevel.addOrder(wrongPriceOrder))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("doesn't match level price");
        }
    }

    @Nested
    @DisplayName("Removing Orders")
    class RemovingOrders {

        @Test
        @DisplayName("should remove specific order and update metrics")
        void shouldRemoveSpecificOrder() {
            // Given
            Order order1 = createOrder("500");
            Order order2 = createOrder("300");
            priceLevel.addOrder(order1);
            priceLevel.addOrder(order2);

            // When
            var removed = priceLevel.removeOrder(order1.orderId());

            // Then
            assertThat(removed).isEqualTo(order1);
            assertThat(priceLevel.getOrderCount()).isEqualTo(1);
            assertThat(priceLevel.getTotalQuantity()).isEqualByComparingTo("300");
            assertThat(priceLevel.peekFirst()).isEqualTo(order2);
        }

        @Test
        @DisplayName("should poll first order in FIFO order")
        void shouldPollFirstOrder() {
            // Given
            Order order1 = createOrder("100");
            Order order2 = createOrder("200");
            priceLevel.addOrder(order1);
            priceLevel.addOrder(order2);

            // When
            var polled = priceLevel.pollFirst();

            // Then
            assertThat(polled).isEqualTo(order1);
            assertThat(priceLevel.getOrderCount()).isEqualTo(1);
            assertThat(priceLevel.getTotalQuantity()).isEqualByComparingTo("200");
            assertThat(priceLevel.peekFirst()).isEqualTo(order2);
        }

        @Test
        @DisplayName("should handle removing all orders")
        void shouldHandleRemovingAllOrders() {
            // Given
            Order order = createOrder("1000");
            priceLevel.addOrder(order);

            // When
            var removed = priceLevel.removeOrder(order.orderId());

            // Then
            assertThat(removed).isEqualTo(order);
            assertThat(priceLevel.isEmpty()).isTrue();
            assertThat(priceLevel.getOrderCount()).isZero();
            assertThat(priceLevel.getTotalQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(priceLevel.peekFirst()).isNull();
        }

        @Test
        @DisplayName("should return null for non-existent order")
        void shouldReturnNullForNonExistentOrder() {
            // Given
            priceLevel.addOrder(createOrder("1000"));

            // When
            var removed = priceLevel.removeOrder("NON-EXISTENT");

            // Then
            assertThat(removed).isNull();
            assertThat(priceLevel.getOrderCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle poll on empty level")
        void shouldHandlePollOnEmpty() {
            // When
            var polled = priceLevel.pollFirst();

            // Then
            assertThat(polled).isNull();
            assertThat(priceLevel.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("should reject null order ID")
        void shouldRejectNullOrderId() {
            assertThatThrownBy(() -> priceLevel.removeOrder(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Order ID cannot be null");
        }
    }

    @Nested
    @DisplayName("Quantity Tracking")
    class QuantityTracking {

        @Test
        @DisplayName("should track quantities accurately with decimal values")
        void shouldTrackDecimalQuantities() {
            // Given - using decimal quantities
            Order order1 = createOrderWithQuantity("100.5");
            Order order2 = createOrderWithQuantity("250.25");
            Order order3 = createOrderWithQuantity("149.25");

            // When
            priceLevel.addOrder(order1);
            priceLevel.addOrder(order2);
            priceLevel.addOrder(order3);

            // Then
            assertThat(priceLevel.getTotalQuantity()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("should update quantities correctly on removal")
        void shouldUpdateQuantitiesOnRemoval() {
            // Given
            Order order1 = createOrder("300");
            Order order2 = createOrder("200");
            Order order3 = createOrder("500");

            priceLevel.addOrder(order1);
            priceLevel.addOrder(order2);
            priceLevel.addOrder(order3);

            // When - remove middle order
            priceLevel.removeOrder(order2.orderId());

            // Then
            assertThat(priceLevel.getTotalQuantity()).isEqualByComparingTo("800");
            assertThat(priceLevel.getOrderCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Order Retrieval")
    class OrderRetrieval {

        @Test
        @DisplayName("should return snapshot of orders")
        void shouldReturnSnapshotOfOrders() {
            // Given
            Order order1 = createOrder("100");
            Order order2 = createOrder("200");
            priceLevel.addOrder(order1);
            priceLevel.addOrder(order2);

            // When
            var orders = priceLevel.getOrders();

            // Modify the returned queue
            orders.poll();
            orders.add(createOrder("999"));

            // Then - original price level should be unchanged
            assertThat(priceLevel.getOrderCount()).isEqualTo(2);
            assertThat(priceLevel.peekFirst()).isEqualTo(order1);
        }
    }

    @Nested
    @DisplayName("Equality and String Representation")
    class EqualityAndString {

        @Test
        @DisplayName("should implement equality based on price")
        void shouldImplementEqualityBasedOnPrice() {
            // Given
            var level1 = new PriceLevel(new BigDecimal("100.00"));
            var level2 = new PriceLevel(new BigDecimal("100.00"));
            var level3 = new PriceLevel(new BigDecimal("100.01"));

            // Then
            assertThat(level1).isEqualTo(level2);
            assertThat(level1).isNotEqualTo(level3);
            assertThat(level1.hashCode()).isEqualTo(level2.hashCode());
        }

        @Test
        @DisplayName("should provide meaningful string representation")
        void shouldProvideStringRepresentation() {
            // Given
            priceLevel.addOrder(createOrder("500"));
            priceLevel.addOrder(createOrder("300"));

            // When
            String representation = priceLevel.toString();

            // Then
            assertThat(representation)
                    .contains("price=100.00")
                    .contains("orders=2")
                    .contains("quantity=800");
        }
    }

    private Order createOrder(String quantity) {
        return createOrderWithQuantity(quantity);
    }

    private Order createOrderWithQuantity(String quantity) {
        return new Order(
                UUID.randomUUID().toString(),
                OrderType.LIMIT,
                Side.BUY,
                testPrice,
                new BigDecimal(quantity),
                "TRADER-1",
                Instant.now()
        );
    }
}