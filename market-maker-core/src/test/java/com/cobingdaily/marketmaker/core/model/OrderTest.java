package com.cobingdaily.marketmaker.core.model;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;

@DisplayName("Order Test")
public class OrderTest {

    private static final Instant TEST_TIMESTAMP = Instant.now();

    @Nested
    @DisplayName("Order Creation")
    class OrderCreation {

        @Test
        @DisplayName("should create valid limit order")
        void shouldCreateValidLimitOrder() {
            // Given
            var orderId = "ORDER-69";
            var price = new BigDecimal("100.5");
            var quantity = new BigDecimal("1000");

            // When
            var order = new Order(
                    orderId,
                    OrderType.LIMIT,
                    Side.BUY,
                    price,
                    quantity,
                    "TRADER-420",
                    TEST_TIMESTAMP
            );

            // Then
            assertThat(order.orderId()).isEqualTo(orderId);
            assertThat(order.type()).isEqualTo(OrderType.LIMIT);
            assertThat(order.side()).isEqualTo(Side.BUY);
            assertThat(order.price()).isEqualByComparingTo("100.50");
            assertThat(order.quantity()).isEqualByComparingTo("1000.00");
            assertThat(order.traderId()).isEqualTo("TRADER-420");
            assertThat(order.timestamp()).isEqualTo(TEST_TIMESTAMP);
        }

        @Test
        @DisplayName("should create valid market order")
        void shouldCreateValidMarketOrder() {
            // Given
            var orderId = "ORDER-70";
            var quantity = new BigDecimal("500");

            // When
            var order = new Order(
                    orderId,
                    OrderType.MARKET,
                    Side.SELL,
                    null,
                    quantity,
                    "TRADER-421",
                    TEST_TIMESTAMP
            );

            // Then
            assertThat(order.orderId()).isEqualTo(orderId);
            assertThat(order.type()).isEqualTo(OrderType.MARKET);
            assertThat(order.side()).isEqualTo(Side.SELL);
            assertThat(order.price()).isNull();
            assertThat(order.quantity()).isEqualByComparingTo("500.00");
            assertThat(order.traderId()).isEqualTo("TRADER-421");
            assertThat(order.timestamp()).isEqualTo(TEST_TIMESTAMP);
        }

        @Test
        @DisplayName("should reject null orderId")
        void shouldRejectNullOrderId() {
            // Given
            String orderId = null;
            var type = OrderType.LIMIT;
            var side = Side.BUY;
            var price = new BigDecimal("100");
            var quantity = new BigDecimal("10");
            var traderId = "TRADER-1";
            var timestamp = TEST_TIMESTAMP;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Order ID cannot be null");
        }

        @Test
        @DisplayName("should reject blank orderId")
        void shouldRejectBlankOrderId() {
            // Given
            var orderId = "   ";
            var type = OrderType.LIMIT;
            var side = Side.BUY;
            var price = new BigDecimal("100");
            var quantity = new BigDecimal("10");
            var traderId = "TRADER-1";
            var timestamp = TEST_TIMESTAMP;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Order ID cannot be blank");
        }

        @Test
        @DisplayName("should reject null type")
        void shouldRejectNullType() {
            // Given
            var orderId = "ORDER-1";
            OrderType type = null;
            var side = Side.BUY;
            var price = new BigDecimal("100");
            var quantity = new BigDecimal("10");
            var traderId = "TRADER-1";
            var timestamp = TEST_TIMESTAMP;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Order type cannot be null");
        }

        @Test
        @DisplayName("should reject null side")
        void shouldRejectNullSide() {
            // Given
            var orderId = "ORDER-1";
            var type = OrderType.LIMIT;
            Side side = null;
            var price = new BigDecimal("100");
            var quantity = new BigDecimal("10");
            var traderId = "TRADER-1";
            var timestamp = TEST_TIMESTAMP;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Side cannot be null");
        }

        @Test
        @DisplayName("should reject null quantity")
        void shouldRejectNullQuantity() {
            // Given
            var orderId = "ORDER-1";
            var type = OrderType.LIMIT;
            var side = Side.BUY;
            var price = new BigDecimal("100");
            BigDecimal quantity = null;
            var traderId = "TRADER-1";
            var timestamp = TEST_TIMESTAMP;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Quantity cannot be null");
        }

        @Test
        @DisplayName("should reject negative quantity")
        void shouldRejectNegativeQuantity() {
            // Given
            var orderId = "ORDER-1337";
            var type = OrderType.LIMIT;
            var side = Side.BUY;
            var price = new BigDecimal("100");
            var quantity = new BigDecimal("-10");
            var traderId = "TRADER-42";
            var timestamp = TEST_TIMESTAMP;
            
            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Quantity must be positive");
        }

        @Test
        @DisplayName("should reject zero quantity")
        void shouldRejectZeroQuantity() {
            // Given
            var orderId = "ORDER-1";
            var type = OrderType.LIMIT;
            var side = Side.BUY;
            var price = new BigDecimal("100");
            var quantity = BigDecimal.ZERO;
            var traderId = "TRADER-1";
            var timestamp = TEST_TIMESTAMP;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Quantity must be positive");
        }

        @Test
        @DisplayName("should reject null traderId")
        void shouldRejectNullTraderId() {
            // Given
            var orderId = "ORDER-1";
            var type = OrderType.LIMIT;
            var side = Side.BUY;
            var price = new BigDecimal("100");
            var quantity = new BigDecimal("10");
            String traderId = null;
            var timestamp = TEST_TIMESTAMP;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Trader ID cannot be null");
        }

        @Test
        @DisplayName("should reject blank traderId")
        void shouldRejectBlankTraderId() {
            // Given
            var orderId = "ORDER-1";
            var type = OrderType.LIMIT;
            var side = Side.BUY;
            var price = new BigDecimal("100");
            var quantity = new BigDecimal("10");
            var traderId = "  ";
            var timestamp = TEST_TIMESTAMP;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Trader ID cannot be blank");
        }

        @Test
        @DisplayName("should reject null timestamp")
        void shouldRejectNullTimestamp() {
            // Given
            var orderId = "ORDER-1";
            var type = OrderType.LIMIT;
            var side = Side.BUY;
            var price = new BigDecimal("100");
            var quantity = new BigDecimal("10");
            var traderId = "TRADER-1";
            Instant timestamp = null;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Timestamp cannot be null");
        }

        @Test
        @DisplayName("should reject null price for limit order")
        void shouldRejectNullPriceForLimitOrder() {
            // Given
            var orderId = "ORDER-1";
            var type = OrderType.LIMIT;
            var side = Side.BUY;
            BigDecimal price = null;
            var quantity = new BigDecimal("10");
            var traderId = "TRADER-1";
            var timestamp = TEST_TIMESTAMP;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Price is required for LIMIT orders");
        }

        @Test
        @DisplayName("should reject negative price for limit order")
        void shouldRejectNegativePriceForLimitOrder() {
            // Given
            var orderId = "ORDER-1";
            var type = OrderType.LIMIT;
            var side = Side.BUY;
            var price = new BigDecimal("-100");
            var quantity = new BigDecimal("10");
            var traderId = "TRADER-1";
            var timestamp = TEST_TIMESTAMP;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Price must be positive for limit order");
        }

        @Test
        @DisplayName("should reject zero price for limit order")
        void shouldRejectZeroPriceForLimitOrder() {
            // Given
            var orderId = "ORDER-1";
            var type = OrderType.LIMIT;
            var side = Side.BUY;
            var price = BigDecimal.ZERO;
            var quantity = new BigDecimal("10");
            var traderId = "TRADER-1";
            var timestamp = TEST_TIMESTAMP;

            // When/Then
            assertThatThrownBy(() ->
                    new Order(orderId, type, side, price, quantity, traderId, timestamp)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Price must be positive for limit order");
        }
    }

    @Nested
    @DisplayName("getValue() method")
    class GetValue {

        @Test
        @DisplayName("should calculate value for limit order")
        void shouldCalculateValueForLimitOrder() {
            // Given
            var order = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100.50"),
                    new BigDecimal("10.5"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );

            // When
            var value = order.getValue();

            // Then
            assertThat(value).isEqualByComparingTo("1055.25");
        }

        @Test
        @DisplayName("should return zero for market order")
        void shouldReturnZeroForMarketOrder() {
            // Given
            var order = new Order(
                    "ORDER-1",
                    OrderType.MARKET,
                    Side.BUY,
                    null,
                    new BigDecimal("100"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );

            // When
            var value = order.getValue();

            // Then
            assertThat(value).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should handle large values")
        void shouldHandleLargeValues() {
            // Given
            var order = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.SELL,
                    new BigDecimal("999999.99"),
                    new BigDecimal("999999.99"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );

            // When
            var value = order.getValue();

            // Then
            assertThat(value).isEqualByComparingTo("999999980000.0001");
        }

        @Test
        @DisplayName("should handle fractional values")
        void shouldHandleFractionalValues() {
            // Given
            var order = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("0.01"),
                    new BigDecimal("0.01"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );

            // When
            var value = order.getValue();

            // Then
            assertThat(value).isEqualByComparingTo("0.0001");
        }
    }

    @Nested
    @DisplayName("canMatch() method")
    class CanMatch {

        @Test
        @DisplayName("should not match orders on same side")
        void shouldNotMatchOrdersOnSameSide() {
            // Given
            var buyOrder1 = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100"),
                    new BigDecimal("10"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );
            var buyOrder2 = new Order(
                    "ORDER-2",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("101"),
                    new BigDecimal("10"),
                    "TRADER-2",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(buyOrder1.canMatch(buyOrder2)).isFalse();
            assertThat(buyOrder2.canMatch(buyOrder1)).isFalse();
        }

        @Test
        @DisplayName("should match market order with any opposite side order")
        void shouldMatchMarketOrderWithAnyOppositeSideOrder() {
            // Given
            var marketBuy = new Order(
                    "ORDER-1",
                    OrderType.MARKET,
                    Side.BUY,
                    null,
                    new BigDecimal("10"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );
            var limitSell = new Order(
                    "ORDER-2",
                    OrderType.LIMIT,
                    Side.SELL,
                    new BigDecimal("100"),
                    new BigDecimal("10"),
                    "TRADER-2",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(marketBuy.canMatch(limitSell)).isTrue();
            assertThat(limitSell.canMatch(marketBuy)).isTrue();
        }

        @Test
        @DisplayName("should match two market orders on opposite sides")
        void shouldMatchTwoMarketOrdersOnOppositeSides() {
            // Given
            var marketBuy = new Order(
                    "ORDER-1",
                    OrderType.MARKET,
                    Side.BUY,
                    null,
                    new BigDecimal("10"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );
            var marketSell = new Order(
                    "ORDER-2",
                    OrderType.MARKET,
                    Side.SELL,
                    null,
                    new BigDecimal("10"),
                    "TRADER-2",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(marketBuy.canMatch(marketSell)).isTrue();
            assertThat(marketSell.canMatch(marketBuy)).isTrue();
        }

        @Test
        @DisplayName("should match limit orders when buy price >= sell price")
        void shouldMatchLimitOrdersWhenPricesCross() {
            // Given
            var buyOrder = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100"),
                    new BigDecimal("10"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );
            var sellOrder = new Order(
                    "ORDER-2",
                    OrderType.LIMIT,
                    Side.SELL,
                    new BigDecimal("99"),
                    new BigDecimal("10"),
                    "TRADER-2",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(buyOrder.canMatch(sellOrder)).isTrue();
            assertThat(sellOrder.canMatch(buyOrder)).isTrue();
        }

        @Test
        @DisplayName("should match limit orders when prices are equal")
        void shouldMatchLimitOrdersWhenPricesAreEqual() {
            // Given
            var buyOrder = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100"),
                    new BigDecimal("10"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );
            var sellOrder = new Order(
                    "ORDER-2",
                    OrderType.LIMIT,
                    Side.SELL,
                    new BigDecimal("100"),
                    new BigDecimal("10"),
                    "TRADER-2",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(buyOrder.canMatch(sellOrder)).isTrue();
            assertThat(sellOrder.canMatch(buyOrder)).isTrue();
        }

        @Test
        @DisplayName("should not match limit orders when prices don't cross")
        void shouldNotMatchLimitOrdersWhenPricesDontCross() {
            // Given
            var buyOrder = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("99"),
                    new BigDecimal("10"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );
            var sellOrder = new Order(
                    "ORDER-2",
                    OrderType.LIMIT,
                    Side.SELL,
                    new BigDecimal("100"),
                    new BigDecimal("10"),
                    "TRADER-2",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(buyOrder.canMatch(sellOrder)).isFalse();
            assertThat(sellOrder.canMatch(buyOrder)).isFalse();
        }

        @Test
        @DisplayName("should reject null order in canMatch")
        void shouldRejectNullOrderInCanMatch() {
            // Given
            var order = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100"),
                    new BigDecimal("10"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThatThrownBy(() -> order.canMatch(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Other order cannot be null");
        }
    }

    @Nested
    @DisplayName("Decimal Scaling")
    class DecimalScaling {

        @Test
        @DisplayName("should scale price to 2 decimal places")
        void shouldScalePriceTo2DecimalPlaces() {
            // Given
            var order = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100.999"),
                    new BigDecimal("10"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(order.price()).isEqualByComparingTo("101.00");
        }

        @Test
        @DisplayName("should scale quantity to 2 decimal places")
        void shouldScaleQuantityTo2DecimalPlaces() {
            // Given
            var order = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100"),
                    new BigDecimal("10.999"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(order.quantity()).isEqualByComparingTo("11.00");
        }

        @Test
        @DisplayName("should round half up for price")
        void shouldRoundHalfUpForPrice() {
            // Given
            var order1 = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100.125"),
                    new BigDecimal("10"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );
            var order2 = new Order(
                    "ORDER-2",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100.124"),
                    new BigDecimal("10"),
                    "TRADER-2",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(order1.price()).isEqualByComparingTo("100.13");
            assertThat(order2.price()).isEqualByComparingTo("100.12");
        }

        @Test
        @DisplayName("should round half up for quantity")
        void shouldRoundHalfUpForQuantity() {
            // Given
            var order1 = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100"),
                    new BigDecimal("10.125"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );
            var order2 = new Order(
                    "ORDER-2",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100"),
                    new BigDecimal("10.124"),
                    "TRADER-2",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(order1.quantity()).isEqualByComparingTo("10.13");
            assertThat(order2.quantity()).isEqualByComparingTo("10.12");
        }

        @Test
        @DisplayName("should preserve exact 2 decimal values")
        void shouldPreserveExact2DecimalValues() {
            // Given
            var order = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("100.50"),
                    new BigDecimal("10.25"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(order.price()).isEqualByComparingTo("100.50");
            assertThat(order.quantity()).isEqualByComparingTo("10.25");
        }

        @Test
        @DisplayName("should handle very small decimal values")
        void shouldHandleVerySmallDecimalValues() {
            // Given
            var order = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("0.001"),
                    new BigDecimal("0.001"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(order.price()).isEqualByComparingTo("0.00");
            assertThat(order.quantity()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("should handle values that round to minimum positive")
        void shouldHandleValuesThatRoundToMinimumPositive() {
            // Given
            var order = new Order(
                    "ORDER-1",
                    OrderType.LIMIT,
                    Side.BUY,
                    new BigDecimal("0.005"),
                    new BigDecimal("0.005"),
                    "TRADER-1",
                    TEST_TIMESTAMP
            );

            // When/Then
            assertThat(order.price()).isEqualByComparingTo("0.01");
            assertThat(order.quantity()).isEqualByComparingTo("0.01");
        }
    }
}