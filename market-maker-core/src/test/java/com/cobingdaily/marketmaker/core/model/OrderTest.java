package com.cobingdaily.marketmaker.core.model;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;

@DisplayName("Order Test")
public class OrderTest {

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
                    Instant.now()
            );

            // Then
            assertThat(order.orderId()).isEqualTo(orderId);
            assertThat(order.price()).isEqualByComparingTo(price);
            assertThat(order.quantity()).isEqualByComparingTo(quantity);
        }

        @Test
        @DisplayName("should reject negative quantity")
        void shouldRejectNegativeQuantity() {
            // Given
            var orderId = "ORDER-1337";

            var price = new BigDecimal("100");
            var quantity = new BigDecimal("-10");
            var timestamp = Instant.now();
            // When/Then
            assertThatThrownBy(() ->
                    new Order(
                            orderId,
                            OrderType.LIMIT,
                            Side.BUY,
                            price,
                            quantity,
                            "TRADER-42",
                            timestamp
                    )
                    )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Quantity must be positive");
        }
    }
}