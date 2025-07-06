package com.cobingdaily.marketmaker.engine;

import com.cobingdaily.marketmaker.core.book.OrderBook;
import com.cobingdaily.marketmaker.core.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The main implementation of the matching engine using price-time priority.
 *
 * <p>This implementation processes orders against an order book, executing
 * trades when prices cross and handling partial fills. It maintains order
 * status tracking separately from the immutable Order objects.
 * <p>Key features:
 * <ul>
 *   <li>Price-time priority matching</li>
 *   <li>Support for market and limit orders</li>
 *   <li>Partial fill handling</li>
 *   <li>Self-trade prevention</li>
 *   <li>Thread-safe operation</li>
 * </ul>
 */
public class MatchingEngineImpl implements MatchingEngine {
    private final OrderBook orderBook;
    private final OrderValidator orderValidator;
    private final TradeGenerator tradeGenerator;

    /** Track order statuses separately since orders are immutable objects.*/
    private final Map<String, OrderStatus> orderStatusMap;

    /** Track remaining quantities for partially filled orders. */
    private final Map<String, BigDecimal> remainingQuantityMap;

    /** Track volumes of traders. */
    private final Map<String, AtomicReference<BigDecimal>> traderVolumeMap;

    /**
     * Creates a new matching engine with the specified order book.
     *
     * @param orderBook the order book to use for matching
     * @throws NullPointerException if orderBook is null
     */
    public MatchingEngineImpl(OrderBook orderBook) {
        this.orderBook = Objects.requireNonNull(orderBook, "Order book cannot be null");
        orderValidator = new OrderValidator();
        tradeGenerator = new TradeGenerator();
        orderStatusMap = new ConcurrentHashMap<>();
        remainingQuantityMap = new ConcurrentHashMap<>();
        traderVolumeMap = new ConcurrentHashMap<>();
    }

    @Override
    public MatchResult processOrder(Order incomingOrder) {
        Objects.requireNonNull(incomingOrder, "Incoming order cannot be null");

        orderValidator.validateOrder(incomingOrder);

        // Initially track the order as new
        orderStatusMap.put(incomingOrder.orderId(), OrderStatus.NEW);
        remainingQuantityMap.put(incomingOrder.orderId(), incomingOrder.quantity());

        // Attempt to match the order
        MatchResult result = matchOrder(incomingOrder);
        orderStatusMap.put(incomingOrder.orderId(), result.finalStatus());

        // If there's a remaining order, add it to the order book
        // Market orders can't be added to the book (no price)
        if (result.remainingOrder().isPresent() && result.finalStatus() != OrderStatus.CANCELLED) {
            Order remainingOrder = result.remainingOrder().get();
            if (remainingOrder.type() == OrderType.LIMIT) {
                orderBook.addOrder(remainingOrder);
            }
        }

        return result;
    }

    @Override
    public OrderStatus getOrderStatus(String orderId) {
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        return orderStatusMap.getOrDefault(orderId, OrderStatus.NEW);
    }

    @Override
    public boolean cancelOrder(String orderId) {
        Objects.requireNonNull(orderId, "Order ID cannot be null");

        // Check if the order exists in the book
        Optional<Order> orderOptional = orderBook.getOrder(orderId);
        if (orderOptional.isEmpty()) {
            return false;
        }

        orderBook.removeOrder(orderId);

        orderStatusMap.put(orderId, OrderStatus.CANCELLED);
        remainingQuantityMap.remove(orderId);

        return true;
    }

    @Override
    public BigDecimal getTradedVolume(String traderId) {
        Objects.requireNonNull(traderId, "Trader ID cannot be null");

        AtomicReference<BigDecimal> volumeReference = traderVolumeMap.get(traderId);

        return volumeReference == null ? BigDecimal.ZERO : volumeReference.get();
    }

    /**
     * Attempts to match an incoming order against the order book.
     *
     * @param incomingOrder the order to match
     * @return the match result
     */
    private MatchResult matchOrder(Order incomingOrder) {
        List<Trade> trades = new ArrayList<>();
        BigDecimal remainingQuantity = incomingOrder.quantity();
        BigDecimal totalValue = BigDecimal.ZERO;

        Side oppositeSide = incomingOrder.side() == Side.BUY ? Side.SELL : Side.BUY;

        Order currentOrder = incomingOrder;

        // Match against orders until we can't match anymore
        while (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            // Get the best price on the opposite side
            Optional<BigDecimal> bestPriceOptional = incomingOrder.side() == Side.BUY ?
                    orderBook.getBestAsk() : orderBook.getBestBid();

            if (bestPriceOptional.isEmpty()) {
                break;
            }

            BigDecimal bestPrice = bestPriceOptional.get();

            Queue<Order> ordersAtPrice = orderBook.getOrdersAtPrice(oppositeSide, bestPrice);
            if (ordersAtPrice == null || ordersAtPrice.isEmpty()) {
                break;
            }

            boolean matchedAtThisLevel = false;

            for (Order passiveOrder : ordersAtPrice) {
                if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                if (!currentOrder.canMatch(passiveOrder)) {
                    continue;
                }

                // Prevent self-trading
                if (!orderValidator.canTrade(currentOrder, passiveOrder)) {
                    continue;
                }

                matchedAtThisLevel = true;

                BigDecimal passiveRemaining = remainingQuantityMap.getOrDefault(
                        passiveOrder.orderId(),
                        passiveOrder.quantity()
                );
                BigDecimal matchQuantity = remainingQuantity.min(passiveRemaining);

                Trade trade = tradeGenerator.generateTrade(
                        incomingOrder,
                        passiveOrder,
                        matchQuantity
                );
                trades.add(trade);

                remainingQuantity = remainingQuantity.subtract(matchQuantity);
                totalValue = totalValue.add(trade.price().multiply(matchQuantity));

                // Update current order for next iteration if partially filled
                if (remainingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                    currentOrder = createRemainingOrder(incomingOrder, remainingQuantity);
                }

                BigDecimal tradeValue = trade.quantity().multiply(trade.price());
                updateTraderVolume(trade.buyer(), tradeValue);
                updateTraderVolume(trade.seller(), tradeValue);

                handlePassiveOrderFill(passiveOrder, matchQuantity);
            }

            // If we couldn't match at this price level, stop trying
            if (!matchedAtThisLevel) {
                break;
            }
        }

        // Determine final status and create a match-result
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            // Fully filled
            BigDecimal avgPrice = calculateAveragePrice(totalValue, incomingOrder.quantity());
            remainingQuantityMap.remove(incomingOrder.orderId());
            return MatchResult.fullyFilled(trades, avgPrice);
        } else if (trades.isEmpty()) {
            // No match
            return MatchResult.noMatch(incomingOrder);
        } else {
            // Partially filled
            BigDecimal filledQuantity = incomingOrder.quantity().subtract(remainingQuantity);
            BigDecimal avgPrice = calculateAveragePrice(totalValue, filledQuantity);

            remainingQuantityMap.put(incomingOrder.orderId(), remainingQuantity);

            return MatchResult.partiallyFilled(trades, currentOrder, avgPrice);
        }
    }
    /**
     * Handles the fill of a passive order.
     */
    private void handlePassiveOrderFill(Order passiveOrder, BigDecimal filledQuantity) {
        BigDecimal remainingQuantity = remainingQuantityMap.getOrDefault(
                passiveOrder.orderId(),
                passiveOrder.quantity()
        );

        remainingQuantity = remainingQuantity.subtract(filledQuantity);

        if (remainingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            // Fully filled
            orderBook.removeOrder(passiveOrder.orderId());
            orderStatusMap.put(passiveOrder.orderId(), OrderStatus.FILLED);
            remainingQuantityMap.remove(passiveOrder.orderId());
        } else {
            // Partially filled
            orderBook.removeOrder(passiveOrder.orderId());
            Order remainingOrder = createRemainingOrder(passiveOrder, remainingQuantity);
            orderBook.addOrder(remainingOrder);
            orderStatusMap.put(passiveOrder.orderId(), OrderStatus.PARTIALLY_FILLED);
            remainingQuantityMap.put(remainingOrder.orderId(), remainingQuantity);
        }
    }

    /**
     * Creates a new order with the remaining quantity after a partial fill.
     */
    private Order createRemainingOrder(Order originalOrder, BigDecimal remainingQuantity) {
        return new Order(
                originalOrder.orderId(),
                originalOrder.type(),
                originalOrder.side(),
                originalOrder.price(),
                remainingQuantity,
                originalOrder.traderId(),
                originalOrder.timestamp()
        );
    }

    /**
     * Calculates the volume-weighted average price.
     */
    private BigDecimal calculateAveragePrice(BigDecimal totalValue, BigDecimal totalQuantity) {
        if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        //TODO: scale=2 is hard coded and should be managed elsewhere
        return totalValue.divide(totalQuantity, 2, RoundingMode.HALF_UP);
    }

    /**
     * Updates the total traded volume for a trader.
     */
    private void updateTraderVolume(String traderId, BigDecimal volume) {
        traderVolumeMap.computeIfAbsent(traderId, k -> new AtomicReference<>(BigDecimal.ZERO))
                .updateAndGet(current -> current.add(volume));
    }
}
