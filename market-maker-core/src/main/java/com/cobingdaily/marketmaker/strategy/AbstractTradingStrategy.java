package com.cobingdaily.marketmaker.strategy;

import com.cobingdaily.marketmaker.core.book.OrderBook;
import com.cobingdaily.marketmaker.core.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract base class for trading strategies providing common functionality.
 *
 * <p>This class handles the lifecycle management, event dispatching, and
 * provides hooks for concrete strategy implementations. All strategies
 * should extend this class rather than implementing TradingStrategy directly.
 *
 * <p>Key features:
 * <ul>
 *   <li>Thread-safe lifecycle management (start/stop)</li>
 *   <li>Automatic periodic quote updates via scheduler</li>
 *   <li>Event handling with error recovery</li>
 *   <li>Metrics collection hooks</li>
 * </ul>
 *
 * @see TradingStrategy
 * @see StrategyContext
 */
public abstract class AbstractTradingStrategy implements TradingStrategy {
    protected final StrategyConfig config;
    protected final StrategyContext context;
    protected final ScheduledExecutorService scheduler;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ScheduledFuture<?>> refreshTask = new AtomicReference<>();
    private final AtomicReference<Instant> lastUpdateTime = new AtomicReference<>(Instant.now());

    /**
     * Creates a new strategy with the given configuration and context.
     *
     * @param config the strategy configuration
     * @param context the execution context
     * @throws NullPointerException if config or context is null
     */
    protected AbstractTradingStrategy(StrategyConfig config, StrategyContext context) {
        this.config = Objects.requireNonNull(config, "Config cannot be null");
        this.context = Objects.requireNonNull(context, "Context cannot be null");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(x -> {
            var thread = new Thread(x, "Strategy-Scheduler-" + config.traderId());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public final void initialize(StrategyConfig config) {
        // Initialization is handled in the constructor
    }

    @Override
    public final void start() {
        if (running.compareAndSet(false, true)) {
            onStrategyStart();
            schedulePeriodicRefresh();
        }
    }

    @Override
    public final void stop() {
        if (running.compareAndSet(true, false)) {
            cancelPeriodicRefresh();
            onStrategyStop();
            scheduler.shutdown();
            awaitTermination();
        }
    }

    @Override
    public final boolean isRunning() {
        return running.get();
    }

    @Override
    public final void onMarketData(OrderBook orderBook) {
        if (!isRunning()) {
            return;
        }

        try {
            lastUpdateTime.set(Instant.now());
            handleMarketData(orderBook);
        } catch (Exception e) {
            handleError("Market data processing error", e);
        }
    }

    @Override
    public final void onTrade(Trade trade) {
        if (!isRunning()) {
            return;
        }

        try {
            handleTrade(trade);
        } catch (Exception e) {
            handleError("Trade processing error", e);
        }
    }

    @Override
    public final void onOrderUpdate(Order order, OrderStatus status) {
        if (!isRunning()) {
            return;
        }

        try {
            handleOrderUpdate(order, status);
        } catch (Exception e) {
            handleError("Order update processing error", e);
        }
    }

    /**
     * Called when the strategy is started.
     * Override to perform initialization logic.
     */
    protected abstract void onStrategyStart();

    /**
     * Called when the strategy is stopped.
     * Override to perform cleanup logic.
     */
    protected abstract void onStrategyStop();

    /**
     * Handles market data updates.
     *
     * @param orderBook the current order book state
     */
    protected abstract void handleMarketData(OrderBook orderBook);

    /**
     * Handles trade execution notifications.
     *
     * @param trade the executed trade
     */
    protected abstract void handleTrade(Trade trade);

    /**
     * Handles order status updates.
     *
     * @param order the order that was updated
     * @param status the new status
     */
    protected abstract void handleOrderUpdate(Order order, OrderStatus status);

    /**
     * Called periodically to refresh quotes.
     * Override to implement quote update logic.
     */
    protected abstract void refreshQuotes();

    /**
     * Handles errors during strategy execution.
     * Override to implement custom error handling.
     *
     * @param message error context message
     * @param error the exception that occurred
     */
    protected void handleError(String message, Exception error) {
        System.err.println(config.traderId() + " - " + message + ": " + error.getMessage());
    }

    /**
     * Gets the time since the last market data update.
     *
     * @return duration since last update
     */
    protected Duration getTimeSinceLastUpdate() {
        return Duration.between(lastUpdateTime.get(), Instant.now());
    }

    private void schedulePeriodicRefresh() {
        var task = scheduler.scheduleAtFixedRate(
                this::safeRefreshQuotes,
                config.orderRefreshInterval().toMillis(),
                config.orderRefreshInterval().toMillis(),
                TimeUnit.MILLISECONDS
        );
        refreshTask.set(task);
    }

    private void cancelPeriodicRefresh() {
        var task = refreshTask.getAndSet(null);
        if (task != null) {
            task.cancel(false);
        }
    }

    private void safeRefreshQuotes() {
        try {
            if (isRunning()) {
                refreshQuotes();
            }
        } catch (Exception e) {
            handleError("Quote refresh error", e);
        }
    }

    private void awaitTermination() {
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

