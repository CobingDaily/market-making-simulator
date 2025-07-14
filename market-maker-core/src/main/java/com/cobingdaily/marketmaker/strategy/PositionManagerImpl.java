package com.cobingdaily.marketmaker.strategy;

import com.cobingdaily.marketmaker.core.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe implementation of {@link PositionManager}.
 *
 * <p>This class tracks trading positions and calculates position-related metrics.
 * It uses atomic operations to ensure thread safety in concurrent environments.
 *
 * @see PositionManager
 * @see Position
 */
public class PositionManagerImpl implements PositionManager {
    private static final int PRICE_SCALE = 2;
    private static final int QUANTITY_SCALE = 2;

    private final BigDecimal maxPosition;
    private final String traderId;
    private final AtomicReference<PositionState> positionState;

    /**
     * Internal mutable state for position tracking.
     */
    private static class PositionState {
        BigDecimal totalBought = BigDecimal.ZERO;
        BigDecimal totalSold = BigDecimal.ZERO;
        BigDecimal totalBoughtValue = BigDecimal.ZERO;
        BigDecimal totalSoldValue = BigDecimal.ZERO;
        BigDecimal realizedPnL = BigDecimal.ZERO;
        Instant openTime = Instant.now();
        Instant lastUpdateTime = Instant.now();

        Position toPosition() {
            var netQuantity = totalBought.subtract(totalSold);
            var avgBuyPrice = calculateAveragePrice(totalBoughtValue, totalBought);
            var avgSellPrice = calculateAveragePrice(totalSoldValue, totalSold);
            var turnover = totalBought.add(totalSold);

            return new Position(
                    netQuantity,
                    totalBought,
                    totalSold,
                    avgBuyPrice,
                    avgSellPrice,
                    realizedPnL,
                    lastUpdateTime,
                    openTime,
                    turnover
            );
        }

        private BigDecimal calculateAveragePrice(BigDecimal totalValue, BigDecimal totalQuantity) {
            if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return totalValue.divide(totalQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
        }
    }

    /**
     * Creates a new position manager.
     *
     * @param maxPosition the maximum allowed position size
     * @param traderId the ID of the trader
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if maxPosition is not positive
     */
    public PositionManagerImpl(BigDecimal maxPosition, String traderId) {
        this.maxPosition = Objects.requireNonNull(maxPosition, "Max position cannot be null");
        this.traderId = Objects.requireNonNull(traderId, "Trader ID cannot be null");

        if (maxPosition.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Max position must be positive");
        }

        this.positionState = new AtomicReference<>(new PositionState());
    }

    @Override
    public BigDecimal getCurrentPosition() {
        var state = positionState.get();
        return state.totalBought.subtract(state.totalSold);
    }

    @Override
    public Position getPositionDetails() {
        return positionState.get().toPosition();
    }

    @Override
    public void updatePosition(Trade trade) {
        Objects.requireNonNull(trade, "Trade cannot be null");

        positionState.updateAndGet(state -> {
            var newState = copyState(state);
            newState.lastUpdateTime = Instant.now();

            if (trade.buyer().equals(traderId)) {
                handleBuyTrade(newState, trade);
            } else if (trade.seller().equals(traderId)) {
                handleSellTrade(newState, trade);
            }

            return newState;
        });
    }

    @Override
    public boolean canAcceptOrder(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");

        var currentNet = getCurrentPosition();
        var orderImpact = calculateOrderImpact(order);
        var projectedPosition = currentNet.add(orderImpact);

        return projectedPosition.compareTo(BigDecimal.ZERO) <= 0;
    }

    @Override
    public BigDecimal getMaxPosition() {
        return maxPosition;
    }

    @Override
    public BigDecimal getPositionUtilization() {
        var currentPosition = getCurrentPosition().abs();
        if (maxPosition.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return currentPosition
                .divide(maxPosition, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public void reset() {
        positionState.set(new PositionState());
    }

    /**
     * Handles a buy trade execution.
     */
    private void handleBuyTrade(PositionState state, Trade trade) {
        state.totalBought.add(trade.quantity());
        state.totalBoughtValue.add(
                trade.price().multiply(trade.quantity())
        );

        // If we were short, calculate realized P&L
        var previousNet = state.totalBought.subtract(state.totalSold).subtract(trade.quantity());
        if (previousNet.compareTo(BigDecimal.ZERO) < 0) {
            calculateRealizedPnL(state, trade, previousNet);
        }
    }

    /**
     * Handles a sell trade execution.
     */
    private void handleSellTrade(PositionState state, Trade trade) {
        state.totalSold = state.totalSold.add(trade.quantity());
        state.totalSoldValue = state.totalSoldValue.add(
                trade.price().multiply(trade.quantity())
        );

        // If we were long, calculate realized P&L
        var previousNet = state.totalBought.subtract(state.totalSold).add(trade.quantity());
        if (previousNet.compareTo(BigDecimal.ZERO) > 0) {
            calculateRealizedPnL(state, trade, previousNet);
        }
    }

    /**
     * Calculates realized P&L when closing or reducing a position.
     */
    private void calculateRealizedPnL(PositionState state, Trade trade, BigDecimal previousNet) {
        var closingQuantity = trade.quantity().min(previousNet.abs());

        if (closingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pnl;
            if (previousNet.compareTo(BigDecimal.ZERO) > 0) {
                // Closing the long position by selling
                var avgBuyPrice = state.totalBoughtValue.divide(state.totalBought, PRICE_SCALE, RoundingMode.HALF_UP);
                pnl = trade.price().subtract(avgBuyPrice).multiply(closingQuantity);
            }
            else {
                // Closing the short position by buying
                var avgBuyPrice = state.totalSoldValue.divide(state.totalSold, PRICE_SCALE, RoundingMode.HALF_UP);
                pnl = avgBuyPrice.subtract(trade.price()).multiply(closingQuantity);
            }
            state.realizedPnL = state.realizedPnL.add(pnl);
        }
    }

    /**
     * Calculates the position impact of an order.
     */
    private BigDecimal calculateOrderImpact(Order order) {
        return order.side() == Side.BUY ? order.quantity() : order.quantity().negate();
    }

    /**
     * Creates a copy of the position state.
     */
    private PositionState copyState(PositionState original) {
        var copy = new PositionState();
        copy.totalBought = original.totalBought;
        copy.totalSold = original.totalSold;
        copy.totalBoughtValue = original.totalBoughtValue;
        copy.totalSoldValue = original.totalSoldValue;
        copy.realizedPnL = original.realizedPnL;
        copy.openTime = original.openTime;
        copy.lastUpdateTime = original.lastUpdateTime;
        return copy;
    }
}
