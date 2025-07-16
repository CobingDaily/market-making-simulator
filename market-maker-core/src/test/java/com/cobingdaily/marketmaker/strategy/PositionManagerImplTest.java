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
    @DisplayName("Thread Safety")
    class ThreadSafety {

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