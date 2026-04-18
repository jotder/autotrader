package com.rj.engine;

import com.rj.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Active order registry with symbol-level locking and timeout handling.
 * <p>
 * Thread-safe — all collections are concurrent. Symbol locks use
 * {@link ReentrantLock} with {@code tryLock()} to avoid blocking candle threads.
 */
public final class OrderTracker {

    private static final Logger log = LoggerFactory.getLogger(OrderTracker.class);
    private static final int MAX_COMPLETED_HISTORY = 500;

    private final ConcurrentHashMap<String, ManagedOrder> activeOrders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ManagedOrder> brokerIdMap = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<ManagedOrder> completedOrders = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, ReentrantLock> symbolLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> attemptCount = new ConcurrentHashMap<>();

    private final Duration orderTimeout;
    private final List<OrderStateListener> listeners = new CopyOnWriteArrayList<>();

    public OrderTracker(Duration orderTimeout) {
        this.orderTimeout = orderTimeout;
    }

    public void addListener(OrderStateListener listener) {
        listeners.add(listener);
    }

    void notifyListeners(ManagedOrder order, ManagedOrder.StateTransition transition) {
        for (OrderStateListener listener : listeners) {
            try {
                listener.onStateChange(order, transition);
            } catch (Exception e) {
                log.error("[OMS] Listener failed for {}: {}", order.getClientOrderId(), e.getMessage());
            }
        }
    }

    // ── Symbol lock ─────────────────────────────────────────────────────────

    /** Get or create a per-symbol lock for entry order serialization. */
    public ReentrantLock lockForSymbol(String symbol) {
        return symbolLocks.computeIfAbsent(symbol, _ -> new ReentrantLock());
    }

    // ── Order lifecycle ─────────────────────────────────────────────────────

    public ManagedOrder createOrder(String clientOrderId, String correlationId,
                                    String symbol, OrderSideType side,
                                    Signal direction, int quantity,
                                    String strategyId) {
        var order = new ManagedOrder(clientOrderId, correlationId, symbol,
                side, direction, quantity, strategyId, this);
        activeOrders.put(clientOrderId, order);
        return order;
    }

    public void linkBrokerId(String clientOrderId, String brokerOrderId) {
        ManagedOrder order = activeOrders.get(clientOrderId);
        if (order != null && brokerOrderId != null) {
            brokerIdMap.put(brokerOrderId, order);
        }
    }

    public int getNextAttempt(String correlationId, OrderSideType side) {
        String key = correlationId + ":" + side.name();
        return attemptCount.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
    }

    public Optional<ManagedOrder> getByClientOrderId(String id) {
        return Optional.ofNullable(activeOrders.get(id));
    }

    public Optional<ManagedOrder> getByBrokerOrderId(String id) {
        return Optional.ofNullable(brokerIdMap.get(id));
    }

    public boolean isDuplicate(String clientOrderId) {
        return activeOrders.containsKey(clientOrderId) || completedOrders.stream().anyMatch(o -> o.getClientOrderId().equals(clientOrderId));
    }

    public boolean hasOrderForCorrelationId(String correlationId, OrderSideType side) {
        // Check active orders
        boolean active = activeOrders.values().stream()
                .anyMatch(o -> o.getCorrelationId().equals(correlationId) && o.getSide() == side);
        if (active) return true;

        // Check completed successful orders (ignore rejections for retryability)
        return completedOrders.stream()
                .anyMatch(o -> o.getCorrelationId().equals(correlationId) 
                        && o.getSide() == side 
                        && (o.getState() == OrderState.FILLED || o.getState() == OrderState.PARTIALLY_FILLED));
    }

    public Optional<ManagedOrder> getOrder(String clientOrderId) {
        ManagedOrder order = activeOrders.get(clientOrderId);
        return Optional.ofNullable(order);
    }

    public Collection<ManagedOrder> activeOrders() {
        return Collections.unmodifiableCollection(activeOrders.values());
    }

    public List<ManagedOrder> activeOrdersForSymbol(String symbol) {
        return activeOrders.values().stream()
                .filter(o -> o.getSymbol().equals(symbol) && !o.isTerminal())
                .collect(Collectors.toList());
    }

    /**
     * Move a terminal order from active to completed history.
     */
    public void onOrderTerminal(ManagedOrder order) {
        activeOrders.remove(order.getClientOrderId());
        completedOrders.addFirst(order);
        while (completedOrders.size() > MAX_COMPLETED_HISTORY) {
            completedOrders.removeLast();
        }
    }

    // ── Timeout sweep ───────────────────────────────────────────────────────

    /** Expire orders that have been non-terminal for longer than the timeout. */
    public void expireTimedOutOrders() {
        Instant cutoff = Instant.now().minus(orderTimeout);
        for (ManagedOrder order : activeOrders.values()) {
            if (!order.isTerminal() && order.getCreatedAt().isBefore(cutoff)) {
                boolean transitioned = order.transitionTo(OrderState.EXPIRED,
                        null, 0, 0, "Timed out after " + orderTimeout);
                if (transitioned) {
                    log.warn("[OMS] Order expired: {}", order.getClientOrderId());
                    onOrderTerminal(order);
                }
            }
        }
    }

    /** Completed order history (most recent first). */
    public Collection<ManagedOrder> completedOrders() {
        return Collections.unmodifiableCollection(completedOrders);
    }

    public int activeCount() { return activeOrders.size(); }
}
