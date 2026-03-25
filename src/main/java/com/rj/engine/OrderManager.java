package com.rj.engine;

import com.rj.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * OMS facade — wraps {@link IOrderExecutor} with state tracking,
 * symbol-level locking, and idempotent order IDs.
 * <p>
 * Entry orders acquire a per-symbol lock to prevent concurrent entries.
 * Exit orders always go through (capital protection).
 * <p>
 * All three executor implementations (Paper, Backtest, Live) work
 * unchanged — this layer wraps them transparently.
 */
public final class OrderManager {

    private static final Logger log = LoggerFactory.getLogger(OrderManager.class);

    private final IOrderExecutor executor;
    private final OrderTracker tracker;
    private final TradeJournal journal;
    private final ScheduledExecutorService timeoutScheduler;

    public OrderManager(IOrderExecutor executor, OrderTracker tracker, TradeJournal journal) {
        this.executor = executor;
        this.tracker = tracker;
        this.journal = journal;

        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("oms-timeout-sweep").factory());
        this.timeoutScheduler.scheduleAtFixedRate(
                tracker::expireTimedOutOrders, 10, 10, TimeUnit.SECONDS);
    }

    // ── Entry order (with symbol lock + dedup) ──────────────────────────────

    /**
     * Submit an entry order with idempotency and symbol-level exclusion.
     *
     * @param signal   the trade signal (carries correlationId)
     * @param quantity pre-computed from risk/sizing layer
     * @return fill result — same type as raw executor for backward compat
     */
    public OrderFill submitEntry(TradeSignal signal, int quantity) {
        String clientOrderId = generateClientOrderId(
                signal.getCorrelationId(), OrderSideType.ENTRY, 1);

        // Idempotency check
        if (tracker.isDuplicate(clientOrderId)) {
            log.warn("[OMS][{}] Duplicate entry order blocked: {}", signal.getSymbol(), clientOrderId);
            return OrderFill.rejected("Duplicate order: " + clientOrderId);
        }

        // Symbol-level lock (non-blocking — reject on contention)
        ReentrantLock symbolLock = tracker.lockForSymbol(signal.getSymbol());
        if (!symbolLock.tryLock()) {
            log.warn("[OMS][{}] Symbol lock contention — entry in progress", signal.getSymbol());
            return OrderFill.rejected("Symbol locked: concurrent entry in progress");
        }

        try {
            // Check for existing active entry order on this symbol
            boolean hasActiveEntry = tracker.activeOrdersForSymbol(signal.getSymbol()).stream()
                    .anyMatch(o -> o.getSide() == OrderSideType.ENTRY);
            if (hasActiveEntry) {
                log.warn("[OMS][{}] Active entry already exists", signal.getSymbol());
                return OrderFill.rejected("Active entry order exists for " + signal.getSymbol());
            }

            // Create and track the order
            ManagedOrder order = tracker.createOrder(clientOrderId, signal.getCorrelationId(),
                    signal.getSymbol(), OrderSideType.ENTRY,
                    signal.getDirection(), quantity, signal.getStrategyId());

            order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null);
            tracker.markSubmitted(clientOrderId);

            // Delegate to underlying executor
            OrderFill fill = executor.placeEntry(signal, quantity);

            // Record state transitions based on result
            applyFillResult(order, fill);

            // Move to completed
            tracker.onOrderTerminal(order);

            log.info("[OMS][{}] Entry order {}: {} → fill={}",
                    signal.getSymbol(), clientOrderId, order.getState(), fill);

            return fill;

        } finally {
            symbolLock.unlock();
        }
    }

    // ── Exit order (no symbol lock — exits always go through) ───────────────

    /**
     * Submit an exit order. No symbol lock — capital protection takes priority.
     */
    public OrderFill submitExit(OpenPosition position,
                                PositionMonitor.ExitReason reason,
                                double exitPrice) {
        String clientOrderId = generateClientOrderId(
                position.getCorrelationId(), OrderSideType.EXIT, 1);

        ManagedOrder order = tracker.createOrder(clientOrderId, position.getCorrelationId(),
                position.getSymbol(), OrderSideType.EXIT,
                position.getDirection(), position.getQuantity(),
                position.getStrategyId());

        order.transitionTo(OrderState.SUBMITTED, null, 0, 0, "reason=" + reason);

        OrderFill fill = executor.placeExit(position, reason, exitPrice);

        applyFillResult(order, fill);
        tracker.onOrderTerminal(order);

        log.info("[OMS][{}] Exit order {}: {} → fill={}",
                position.getSymbol(), clientOrderId, order.getState(), fill);

        return fill;
    }

    // ── Shutdown ─────────────────────────────────────────────────────────────

    public void shutdown() {
        timeoutScheduler.shutdownNow();
        log.info("[OMS] OrderManager shut down. Active orders: {}", tracker.activeCount());
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public OrderTracker getTracker() { return tracker; }

    // ── ClientOrderId generation ────────────────────────────────────────────

    /**
     * Generate a deterministic, idempotent client order ID.
     * Format: {@code {correlationId}_{ENTRY|EXIT}_{attempt}}
     */
    static String generateClientOrderId(String correlationId, OrderSideType side, int attempt) {
        return correlationId + "_" + side.name() + "_" + attempt;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void applyFillResult(ManagedOrder order, OrderFill fill) {
        if (fill.isSuccess()) {
            order.transitionTo(OrderState.ACCEPTED, fill.getOrderId(), 0, 0, null);
            order.transitionTo(OrderState.FILLED, fill.getOrderId(),
                    fill.getFillPrice(), fill.getFillQuantity(), null);
        } else {
            order.transitionTo(OrderState.REJECTED, null, 0, 0, fill.getRejectReason());
        }
    }
}
