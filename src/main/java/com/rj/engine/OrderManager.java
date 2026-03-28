package com.rj.engine;

import com.rj.model.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * OMS orchestrator — handles idempotent order placement, state tracking,
 * and asynchronous broker updates.
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

    /**
     * Submit an order with idempotency.
     */
    public OrderFill submit(TradeSignal signal, int quantity, OrderSideType sideType) {
        // Use correlationId for the main duplicate check for entries
        if (sideType == OrderSideType.ENTRY && tracker.hasOrderForCorrelationId(signal.getCorrelationId(), sideType)) {
            log.warn("[OMS][{}] Duplicate entry blocked for correlationId: {}", signal.getSymbol(), signal.getCorrelationId());
            return OrderFill.rejected("Duplicate correlationId: " + signal.getCorrelationId());
        }

        int attempt = tracker.getNextAttempt(signal.getCorrelationId(), sideType);
        String clientOrderId = generateClientOrderId(signal.getCorrelationId(), sideType, attempt);

        ReentrantLock symbolLock = tracker.lockForSymbol(signal.getSymbol());
        if (sideType == OrderSideType.ENTRY && !symbolLock.tryLock()) {
            log.warn("[OMS][{}] Symbol lock contention", signal.getSymbol());
            return OrderFill.rejected("Symbol locked");
        }

        try {
            if (sideType == OrderSideType.ENTRY && !tracker.activeOrdersForSymbol(signal.getSymbol()).isEmpty()) {
                return OrderFill.rejected("Existing active order for " + signal.getSymbol());
            }

            ManagedOrder order = tracker.createOrder(clientOrderId, signal.getCorrelationId(),
                    signal.getSymbol(), sideType, signal.getDirection(), quantity, signal.getStrategyId());

            log.info("[OMS][{}] Submitting {}: {}", signal.getSymbol(), sideType, clientOrderId);
            
            OrderFill fill;
            if (sideType == OrderSideType.ENTRY) {
                fill = executor.placeEntry(signal, quantity);
            } else {
                fill = OrderFill.rejected("Generic exit not supported via TradeSignal");
            }

            processPlacementResult(order, fill);
            return fill;

        } finally {
            if (sideType == OrderSideType.ENTRY) symbolLock.unlock();
        }
    }

    public OrderFill submitEntry(TradeSignal signal, int quantity) {
        return submit(signal, quantity, OrderSideType.ENTRY);
    }

    public OrderFill submitExit(OpenPosition position, PositionMonitor.ExitReason reason, double exitPrice) {
        if (tracker.hasOrderForCorrelationId(position.getCorrelationId(), OrderSideType.EXIT)) {
            log.warn("[OMS][{}] Duplicate exit blocked for correlationId: {}", position.getSymbol(), position.getCorrelationId());
            return OrderFill.rejected("Duplicate exit for correlationId: " + position.getCorrelationId());
        }

        int attempt = tracker.getNextAttempt(position.getCorrelationId(), OrderSideType.EXIT);
        String clientOrderId = generateClientOrderId(position.getCorrelationId(), OrderSideType.EXIT, attempt);

        ManagedOrder order = tracker.createOrder(clientOrderId, position.getCorrelationId(),
                position.getSymbol(), OrderSideType.EXIT, position.getDirection(), 
                position.getQuantity(), position.getStrategyId());

        log.info("[OMS][{}] Submitting EXIT: {} reason={}", position.getSymbol(), clientOrderId, reason);
        OrderFill fill = executor.placeExit(position, reason, exitPrice);
        processPlacementResult(order, fill);
        return fill;
    }

    /**
     * Entry point for asynchronous broker updates (WebSocket).
     */
    public void processBrokerUpdate(JSONObject update) {
        if (update == null) return;
        
        String brokerOrderId = update.optString("id");
        String clientOrderId = update.optString("remarks");
        int status = update.optInt("status");
        double fillPrice = update.optDouble("last_traded_price", 0);
        int filledQty = update.optInt("filled_qty", 0);
        String message = update.optString("message");

        var orderOpt = tracker.getByClientOrderId(clientOrderId)
                .or(() -> tracker.getByBrokerOrderId(brokerOrderId));

        orderOpt.ifPresent(order -> {
            boolean changed = order.updateFromBroker(status, brokerOrderId, fillPrice, filledQty, message);
            if (changed) {
                log.info("[OMS][{}] Async update: {} -> {}", order.getSymbol(), order.getClientOrderId(), order.getState());
                if (order.isTerminal()) {
                    tracker.onOrderTerminal(order);
                }
            }
        });
    }

    private void processPlacementResult(ManagedOrder order, OrderFill fill) {
        if (fill.isSuccess()) {
            order.transitionTo(OrderState.SUBMITTED, fill.getOrderId(), 0, 0, null);
            tracker.linkBrokerId(order.getClientOrderId(), fill.getOrderId());
            
            if (fill.getFillQuantity() > 0) {
                order.transitionTo(OrderState.FILLED, fill.getOrderId(), 
                        fill.getFillPrice(), fill.getFillQuantity(), null);
                tracker.onOrderTerminal(order);
            }
        } else {
            order.transitionTo(OrderState.REJECTED, null, 0, 0, fill.getRejectReason());
            tracker.onOrderTerminal(order);
        }
    }

    public void shutdown() {
        timeoutScheduler.shutdownNow();
        log.info("[OMS] OrderManager shut down.");
    }

    public static String generateClientOrderId(String correlationId, OrderSideType side, int attempt) {
        return String.format("%s_%s_%d", correlationId, side == OrderSideType.ENTRY ? "ENTRY" : "EXIT", attempt);
    }

    public OrderTracker getTracker() { return tracker; }
}
