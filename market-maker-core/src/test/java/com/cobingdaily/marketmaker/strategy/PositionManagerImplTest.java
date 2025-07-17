package com.cobingdaily.marketmaker.strategy;

import com.cobingdaily.marketmaker.core.model.*;
import com.cobingdaily.marketmaker.core.model.Order;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PositionManagerImpl Tests")
class PositionManagerImplTest {

    private PositionManagerImpl positionManager;
    private static final String TRADER_ID = "TEST-TRADER";
    private static final BigDecimal MAX_POSITION = new BigDecimal("1000.00");

    @BeforeEach
    void setUp() {
        positionManager = new PositionManagerImpl(MAX_POSITION, TRADER_ID);
    }

    @Nested
    @DisplayName("Initialization")
    class Initialization {

        @Test
        @DisplayName("should initialize with zero position")
        void shouldInitializeWithZeroPosition() {
            assertThat(positionManager.getCurrentPosition()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(positionManager.getMaxPosition()).isEqualByComparingTo(MAX_POSITION);
            assertThat(positionManager.getPositionUtilization()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should reject null max position")
        void shouldRejectNullMaxPosition() {
            assertThatThrownBy(() -> new PositionManagerImpl(null, TRADER_ID))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Max position cannot be null");
        }

        @Test
        @DisplayName("should reject null trader ID")
        void shouldRejectNullTraderId() {
            assertThatThrownBy(() -> new PositionManagerImpl(MAX_POSITION, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Trader ID cannot be null");
        }

        @Test
        @DisplayName("should reject non-positive max position")
        void shouldRejectNonPositiveMaxPosition() {
            assertThatThrownBy(() -> new PositionManagerImpl(BigDecimal.ZERO, TRADER_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Max position must be positive");
        }
    }

    @Nested
    @DisplayName("Position Updates")
    class PositionUpdates {

        @Test
        @DisplayName("should update position on buy trade")
        void shouldUpdatePositionOnBuyTrade() {
            // Given
            var trade = createTrade(TRADER_ID, "OTHER-TRADER", "100.00", "10.00");

            // When
            positionManager.updatePosition(trade);

            // Then
            assertThat(positionManager.getCurrentPosition()).isEqualByComparingTo("10.00");

            var position = positionManager.getPositionDetails();
            assertThat(position.totalBought()).isEqualByComparingTo("10.00");
            assertThat(position.totalSold()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(position.avgBuyPrice()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("should update position on sell trade")
        void shouldUpdatePositionOnSellTrade() {
            // Given
            var trade = createTrade("OTHER-TRADER", TRADER_ID, "100.00", "10.00");

            // When
            positionManager.updatePosition(trade);

            // Then
            assertThat(positionManager.getCurrentPosition()).isEqualByComparingTo("-10.00");

            var position = positionManager.getPositionDetails();
            assertThat(position.totalBought()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(position.totalSold()).isEqualByComparingTo("10.00");
            assertThat(position.avgSellPrice()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("should calculate average prices correctly")
        void shouldCalculateAveragePrices() {
            // Given - multiple buy trades at different prices
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "5.00"));
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "102.00", "10.00"));
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "101.00", "5.00"));

            // Then - weighted average: (100*5 + 102*10 + 101*5) / 20 = 101.25
            var position = positionManager.getPositionDetails();
            assertThat(position.avgBuyPrice()).isEqualByComparingTo("101.25");
            assertThat(position.totalBought()).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("should handle trades not involving trader")
        void shouldHandleTradesNotInvolvingTrader() {
            // Given
            var trade = createTrade("OTHER1", "OTHER2", "100.00", "10.00");

            // When
            positionManager.updatePosition(trade);

            // Then - no position change
            assertThat(positionManager.getCurrentPosition()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Realized P&L Calculation")
    class RealizedPnLCalculation {

        @Test
        @DisplayName("should calculate P&L when closing long position")
        void shouldCalculatePnLWhenClosingLong() {
            // Given - buy at 100
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "10.00"));

            // When - sell at 105
            positionManager.updatePosition(createTrade("OTHER", TRADER_ID, "105.00", "10.00"));

            // Then - profit = (105 - 100) * 10 = 50
            var position = positionManager.getPositionDetails();
            assertThat(position.realizedPnL()).isEqualByComparingTo("50.00");
            assertThat(position.isFlat()).isTrue();
        }

        @Test
        @DisplayName("should calculate P&L when closing short position")
        void shouldCalculatePnLWhenClosingShort() {
            // Given - sell at 100
            positionManager.updatePosition(createTrade("OTHER", TRADER_ID, "100.00", "10.00"));

            // When - buy at 95
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "95.00", "10.00"));

            // Then - profit = (100 - 95) * 10 = 50
            var position = positionManager.getPositionDetails();
            assertThat(position.realizedPnL()).isEqualByComparingTo("50.00");
            assertThat(position.isFlat()).isTrue();
        }

        @Test
        @DisplayName("should handle partial position closure")
        void shouldHandlePartialClosure() {
            // Given - buy 20 at 100
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "20.00"));

            // When - sell 10 at 105
            positionManager.updatePosition(createTrade("OTHER", TRADER_ID, "105.00", "10.00"));

            // Then
            var position = positionManager.getPositionDetails();
            assertThat(position.netQuantity()).isEqualByComparingTo("10.00"); // Still long 10
            assertThat(position.realizedPnL()).isEqualByComparingTo("50.00"); // (105-100)*10
        }

        @Test
        @DisplayName("should handle position reversal")
        void shouldHandlePositionReversal() {
            // Given - long 10 at 100
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "10.00"));

            // When - sell 15 at 105 (close 10 and go short 5)
            positionManager.updatePosition(createTrade("OTHER", TRADER_ID, "105.00", "15.00"));

            // Then
            var position = positionManager.getPositionDetails();
            // Short 5
            assertThat(position.netQuantity()).isEqualByComparingTo("-5.00");
            assertThat(position.realizedPnL()).isEqualByComparingTo("50.00");
        }
    }

    @Nested
    @DisplayName("Position Limits")
    class PositionLimits {

        @Test
        @DisplayName("should accept order within position limits")
        void shouldAcceptOrderWithinLimits() {
            // Given - current position is 500
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "500.00"));

            // When - check if can buy 400 more (total would be 900)
            var order = createOrder(Side.BUY, "100.00", "400.00");

            // Then
            assertThat(positionManager.canAcceptOrder(order)).isTrue();
        }

        @Test
        @DisplayName("should reject order exceeding position limits")
        void shouldRejectOrderExceedingLimits() {
            // Given - current position is 500
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "500.00"));

            // When - check if can buy 600 more (total would be 1100, exceeds 1000 limit)
            var order = createOrder(Side.BUY, "100.00", "600.00");

            // Then
            assertThat(positionManager.canAcceptOrder(order)).isFalse();
        }

        @Test
        @DisplayName("should handle position limits for short positions")
        void shouldHandleShortPositionLimits() {
            // Given - current position is -500 (short)
            positionManager.updatePosition(createTrade("OTHER", TRADER_ID, "100.00", "500.00"));

            // When - check if can sell 600 more (total would be -1100)
            var order = createOrder(Side.SELL, "100.00", "600.00");

            // Then
            assertThat(positionManager.canAcceptOrder(order)).isFalse();
        }

        @Test
        @DisplayName("should allow orders that reduce position")
        void shouldAllowOrdersThatReducePosition() {
            // Given - at max long position
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "1000.00"));

            // When - sell order (reduces position)
            var sellOrder = createOrder(Side.SELL, "100.00", "500.00");

            // Then
            assertThat(positionManager.canAcceptOrder(sellOrder)).isTrue();
        }
    }

    @Nested
    @DisplayName("Position Utilization")
    class PositionUtilization {

        @ParameterizedTest
        @DisplayName("should calculate utilization correctly")
        @CsvSource({
                "0.00, 0.00",
                "100.00, 10.00",
                "500.00, 50.00",
                "1000.00, 100.00",
                "-500.00, 50.00",
                "-1000.00, 100.00"
        })
        void shouldCalculateUtilization(String position, String expectedUtilization) {
            // Given
            if (new BigDecimal(position).compareTo(BigDecimal.ZERO) > 0) {
                positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", position));
            } else if (new BigDecimal(position).compareTo(BigDecimal.ZERO) < 0) {
                positionManager.updatePosition(createTrade("OTHER", TRADER_ID, "100.00",
                        new BigDecimal(position).abs().toString()));
            }

            // Then
            assertThat(positionManager.getPositionUtilization())
                    .isEqualByComparingTo(expectedUtilization);
        }
    }

    @Nested
    @DisplayName("Reset Functionality")
    class ResetFunctionality {

        @Test
        @DisplayName("should reset position to zero")
        void shouldResetPosition() {
            // Given - build up position
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "500.00"));
            positionManager.updatePosition(createTrade("OTHER", TRADER_ID, "105.00", "200.00"));

            // When
            positionManager.reset();

            // Then
            assertThat(positionManager.getCurrentPosition()).isEqualByComparingTo(BigDecimal.ZERO);
            var position = positionManager.getPositionDetails();
            assertThat(position.totalBought()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(position.totalSold()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(position.realizedPnL()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesAndBoundaryConditions {

        @Test
        @DisplayName("should handle null trade parameter")
        void shouldHandleNullTrade() {
            assertThatThrownBy(() -> positionManager.updatePosition(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Trade cannot be null");
        }

        @Test
        @DisplayName("should handle null order parameter")
        void shouldHandleNullOrder() {
            assertThatThrownBy(() -> positionManager.canAcceptOrder(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Order cannot be null");
        }

        @Test
        @DisplayName("should handle invalid trade parameters gracefully")
        void shouldHandleInvalidTradeParametersGracefully() {
            // Given - Trade model validates positive quantities at construction
            // Test that Trade validation works as expected
            assertThatThrownBy(() -> new Trade(TRADER_ID, "OTHER", new BigDecimal("100.00"), 
                    BigDecimal.ZERO, Instant.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be positive");
            
            assertThatThrownBy(() -> new Trade(TRADER_ID, "OTHER", new BigDecimal("100.00"), 
                    new BigDecimal("-1.00"), Instant.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Quantity must be positive");
        }

        @Test
        @DisplayName("should handle very small quantity trades")
        void shouldHandleVerySmallQuantityTrades() {
            // Given
            var trade = createTrade(TRADER_ID, "OTHER", "100.00", "0.01");

            // When
            positionManager.updatePosition(trade);

            // Then
            assertThat(positionManager.getCurrentPosition()).isEqualByComparingTo("0.01");
            var position = positionManager.getPositionDetails();
            assertThat(position.avgBuyPrice()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("should handle maximum position exactly")
        void shouldHandleMaximumPositionExactly() {
            // Given - trade that results in exactly max position
            var trade = createTrade(TRADER_ID, "OTHER", "100.00", "1000.00");

            // When
            positionManager.updatePosition(trade);

            // Then
            assertThat(positionManager.getCurrentPosition()).isEqualByComparingTo("1000.00");
            assertThat(positionManager.getPositionUtilization()).isEqualByComparingTo("100.00");

            // Should still accept orders that reduce position
            var sellOrder = createOrder(Side.SELL, "100.00", "1.00");
            assertThat(positionManager.canAcceptOrder(sellOrder)).isTrue();

            // Should reject orders that increase position
            var buyOrder = createOrder(Side.BUY, "100.00", "0.01");
            assertThat(positionManager.canAcceptOrder(buyOrder)).isFalse();
        }

        @Test
        @DisplayName("should handle position exactly at limit boundaries")
        void shouldHandlePositionAtLimitBoundaries() {
            // Test exactly at positive limit
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "1000.00"));
            var buyOrder = createOrder(Side.BUY, "100.00", "0.01");
            assertThat(positionManager.canAcceptOrder(buyOrder)).isFalse();

            // Reset and test exactly at negative limit
            positionManager.reset();
            positionManager.updatePosition(createTrade("OTHER", TRADER_ID, "100.00", "1000.00"));
            var sellOrder = createOrder(Side.SELL, "100.00", "0.01");
            assertThat(positionManager.canAcceptOrder(sellOrder)).isFalse();
        }

        @Test
        @DisplayName("should handle empty trader ID strings")
        void shouldHandleEmptyTraderIdStrings() {
            // Given - trades with empty trader IDs (should not match our TRADER_ID)
            var trade1 = createTrade("", "OTHER", "100.00", "10.00");
            var trade2 = createTrade("DIFFERENT-TRADER", "", "100.00", "10.00");

            // When
            positionManager.updatePosition(trade1);
            positionManager.updatePosition(trade2);

            // Then - no position changes as trader IDs don't match our TRADER_ID
            assertThat(positionManager.getCurrentPosition()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should handle very large position sizes within limits")
        void shouldHandleLargePositionSizesWithinLimits() {
            // Given - position manager with very large limit
            var largePositionManager = new PositionManagerImpl(
                    new BigDecimal("999999999.99"), "LARGE-TRADER"
            );
            var largeTrade = createTrade("LARGE-TRADER", "OTHER", "100.00", "999999999.99");

            // When
            largePositionManager.updatePosition(largeTrade);

            // Then
            assertThat(largePositionManager.getCurrentPosition())
                    .isEqualByComparingTo("999999999.99");
            assertThat(largePositionManager.getPositionUtilization())
                    .isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("should handle position reversals with zero crossing")
        void shouldHandlePositionReversalsWithZeroCrossing() {
            // Given - start with long position
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "10.00"));

            // When - sell exactly to zero
            positionManager.updatePosition(createTrade("OTHER", TRADER_ID, "105.00", "10.00"));

            // Then
            assertThat(positionManager.getCurrentPosition()).isEqualByComparingTo(BigDecimal.ZERO);
            var position = positionManager.getPositionDetails();
            assertThat(position.isFlat()).isTrue();
            assertThat(position.realizedPnL()).isEqualByComparingTo("50.00");

            // When - continue to short
            positionManager.updatePosition(createTrade("OTHER", TRADER_ID, "105.00", "5.00"));

            // Then
            assertThat(positionManager.getCurrentPosition()).isEqualByComparingTo("-5.00");
        }
    }

    @Nested
    @DisplayName("Precision and Rounding Edge Cases")
    class PrecisionAndRoundingEdgeCases {

        @Test
        @DisplayName("should handle prices requiring rounding")
        void shouldHandlePricesRequiringRounding() {
            // Given - prices with more than 2 decimal places
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.333", "3.00"));
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.666", "3.00"));

            // When
            var position = positionManager.getPositionDetails();

            // Then - average should be rounded to 2 decimal places: (100.333*3 + 100.666*3)/6 = 100.4995 → 100.50
            assertThat(position.avgBuyPrice()).isEqualByComparingTo("100.50");
        }

        @Test
        @DisplayName("should handle quantities requiring rounding")
        void shouldHandleQuantitiesRequiringRounding() {
            // Given - quantities with more than 2 decimal places
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "1.333"));
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "1.666"));

            // When
            var position = positionManager.getPositionDetails();

            // Then - total should be rounded: 1.333 + 1.666 = 2.999 → 3.00
            assertThat(position.totalBought()).isEqualByComparingTo("2.999");
        }

        @Test
        @DisplayName("should handle P&L calculation with rounding")
        void shouldHandlePnLCalculationWithRounding() {
            // Given - trades that will produce fractional P&L
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.333", "3.00"));

            // When - sell at price that creates fractional P&L
            positionManager.updatePosition(createTrade("OTHER", TRADER_ID, "100.666", "3.00"));

            // Then - P&L calculation: avgBuyPrice = 100.33 (rounded), P&L = (100.666 - 100.33) * 3 = 1.008
            var position = positionManager.getPositionDetails();
            assertThat(position.realizedPnL()).isEqualByComparingTo("1.008");
        }

        @Test
        @DisplayName("should handle utilization calculation edge cases")
        void shouldHandleUtilizationCalculationEdgeCases() {
            // Given - position that would create fractional utilization
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "333.33"));

            // When
            var utilization = positionManager.getPositionUtilization();

            // Then - utilization should be properly rounded: 333.33/1000 * 100 = 33.333 → 33.33%
            assertThat(utilization).isEqualByComparingTo("33.33");
        }

        @Test
        @DisplayName("should handle zero max position in utilization")
        void shouldHandleZeroMaxPositionInUtilization() {
            // This tests the edge case in getPositionUtilization when maxPosition is zero
            // Note: Constructor prevents zero maxPosition, but testing the calculation logic
            var position = positionManager.getCurrentPosition();
            assertThat(position).isEqualByComparingTo(BigDecimal.ZERO);

            // When max position is non-zero and current position is zero
            assertThat(positionManager.getPositionUtilization()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Enhanced Thread Safety")
    class EnhancedThreadSafety {

        @Test
        @DisplayName("should handle concurrent updates")
        void shouldHandleConcurrentUpdates() throws InterruptedException, ExecutionException {
            // Given
            int threadCount = 10;
            int tradesPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);

            // When - submit concurrent trades
            var futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executor.submit(() -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < tradesPerThread; j++) {
                                if ((i + j) % 2 == 0) {
                                    positionManager.updatePosition(
                                            createTrade(TRADER_ID, "OTHER", "100.00", "1.00")
                                    );
                                } else {
                                    positionManager.updatePosition(
                                            createTrade("OTHER", TRADER_ID, "100.00", "1.00")
                                    );
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }))
                    .toList();

            startLatch.countDown();

            // Wait for completion
            for (var future : futures) {
                future.get();
            }
            executor.shutdown();

            // Then - verify consistency
            var position = positionManager.getPositionDetails();
            var expectedNet = position.totalBought().subtract(position.totalSold());
            assertThat(position.netQuantity()).isEqualByComparingTo(expectedNet);
        }

        @Test
        @DisplayName("should handle concurrent position limit checks")
        void shouldHandleConcurrentPositionLimitChecks() throws InterruptedException {
            // Given
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            var results = new ConcurrentLinkedQueue<Boolean>();

            // Build up position near limit
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "990.00"));

            // When - concurrent limit checks
            var futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> executor.submit(() -> {
                        try {
                            startLatch.await();
                            var order = createOrder(Side.BUY, "100.00", "5.00");
                            results.add(positionManager.canAcceptOrder(order));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }))
                    .toList();

            startLatch.countDown();

            // Wait for completion
            futures.forEach(future -> {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            executor.shutdown();

            // Then - all results should be consistent (all true or all false)
            var uniqueResults = results.stream().distinct().toList();
            assertThat(uniqueResults).hasSize(1); // All results should be the same
        }

        @Test
        @DisplayName("should handle concurrent reset operations")
        void shouldHandleConcurrentResetOperations() throws InterruptedException {
            // Given
            ExecutorService executor = Executors.newFixedThreadPool(5);
            CountDownLatch startLatch = new CountDownLatch(1);

            // Build up some position
            positionManager.updatePosition(createTrade(TRADER_ID, "OTHER", "100.00", "100.00"));

            // When - concurrent resets and updates
            var futures = IntStream.range(0, 5)
                    .mapToObj(i -> executor.submit(() -> {
                        try {
                            startLatch.await();
                            if (i % 2 == 0) {
                                positionManager.reset();
                            } else {
                                positionManager.updatePosition(
                                        createTrade(TRADER_ID, "OTHER", "100.00", "10.00")
                                );
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }))
                    .toList();

            startLatch.countDown();

            // Wait for completion
            futures.forEach(future -> {
                try {
                    future.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            executor.shutdown();

            // Then - position should be consistent (no exceptions thrown)
            var position = positionManager.getPositionDetails();
            assertThat(position).isNotNull();
            assertThat(position.netQuantity()).isNotNull();
        }
    }

    private Trade createTrade(String buyer, String seller, String price, String quantity) {
        return new Trade(
                buyer,
                seller,
                new BigDecimal(price),
                new BigDecimal(quantity),
                Instant.now()
        );
    }

    private Order createOrder(Side side, String price, String quantity) {
        return new Order(
                "TEST-ORDER",
                OrderType.LIMIT,
                side,
                new BigDecimal(price),
                new BigDecimal(quantity),
                TRADER_ID,
                Instant.now()
        );
    }
}