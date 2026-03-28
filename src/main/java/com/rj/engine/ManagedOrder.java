package com.rj.engine;

import com.rj.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A single order tracked through its lifecycle by the OMS state machine.
 * <p>
 * Identity fields are immutable. Mutable state (order status, fill info)
 * is guarded by an internal lock and only modified via {@link #transitionTo}.
 */
public final class ManagedOrder {

    private static final Logger log = LoggerFactory.getLogger(ManagedOrder.class);

    // ── Valid state transitions ──────────────────────────────────────────────
    private static final Map<OrderState, Set<OrderState>> VALID_TRANSITIONS = Map.of(
            OrderState.CREATED, Set.of(OrderState.SUBMITTED, OrderState.REJECTED),
            OrderState.SUBMITTED, Set.of(OrderState.ACCEPTED, OrderState.REJECTED, OrderState.EXPIRED, OrderState.FILLED, OrderState.CANCELLED),
            OrderState.ACCEPTED, Set.of(OrderState.FILLED, OrderState.PARTIALLY_FILLED, OrderState.CANCELLED, OrderState.REJECTED),
            OrderState.PARTIALLY_FILLED, Set.of(OrderState.FILLED, OrderState.CANCELLED, OrderState.REJECTED)
    );

    // ── Identity (immutable) ────────────────────────────────────────────────
    private final String clientOrderId;
    private final String correlationId;
    private final String symbol;
    private final OrderSideType side;
    private final Signal direction;
    private final int requestedQuantity;
    private final String strategyId;
    private final Instant createdAt;
    private final OrderTracker tracker;

    // ── Mutable state (guarded by stateLock) ────────────────────────────────
    private final ReentrantLock stateLock = new ReentrantLock();
    private volatile OrderState state;
    private String brokerOrderId;
    private double fillPrice;
    private int filledQuantity;
    private Instant lastUpdatedAt;
    private String rejectReason;
    private final List<StateTransition> history = new ArrayList<>();

    public ManagedOrder(String clientOrderId, String correlationId,
                        String symbol, OrderSideType side, Signal direction,
                        int requestedQuantity, String strategyId, OrderTracker tracker) {
        this.clientOrderId = clientOrderId;
        this.correlationId = correlationId;
        this.symbol = symbol;
        this.side = side;
        this.direction = direction;
        this.requestedQuantity = requestedQuantity;
        this.strategyId = strategyId;
        this.tracker = tracker;
        this.createdAt = Instant.now();
        this.state = OrderState.CREATED;
        this.lastUpdatedAt = this.createdAt;
    }

    // ── State transition ────────────────────────────────────────────────────

    /**
     * Attempt a state transition. Returns {@code true} if the transition was valid
     * and applied, {@code false} if rejected (invalid transition or already terminal).
     */
    public boolean transitionTo(OrderState newState, String brokerOrderId,
                                double fillPrice, int filledQty, String reason) {
        stateLock.lock();
        try {
            if (state.isTerminal()) {
                log.warn("[OMS] Cannot transition terminal order {} from {} to {}",
                        clientOrderId, state, newState);
                return false;
            }

            Set<OrderState> allowed = VALID_TRANSITIONS.getOrDefault(state, Set.of());
            if (!allowed.contains(newState)) {
                log.error("[OMS] Invalid transition for order {}: {} → {}",
                        clientOrderId, state, newState);
                return false;
            }

            OrderState previousState = this.state;
            this.state = newState;
            this.lastUpdatedAt = Instant.now();

            if (brokerOrderId != null) this.brokerOrderId = brokerOrderId;
            if (fillPrice > 0) this.fillPrice = fillPrice;
            if (filledQty > 0) this.filledQuantity = filledQty;
            if (reason != null) this.rejectReason = reason;

            StateTransition transition = new StateTransition(previousState, newState, this.lastUpdatedAt, reason);
            history.add(transition);

            log.debug("[OMS] Order {} transitioned: {} → {} (broker={}, fill={}/{})",
                    clientOrderId, previousState, newState, this.brokerOrderId,
                    this.filledQuantity, requestedQuantity);

            if (tracker != null) {
                tracker.notifyListeners(this, transition);
            }

            return true;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Update order state based on a broker callback (e.g. from WebSocket).
     * Maps broker-specific status codes to internal OrderState.
     */
    public boolean updateFromBroker(int brokerStatus, String brokerId,
                                    double price, int qty, String message) {
        OrderState newState = mapBrokerStatus(brokerStatus);
        if (newState == null) return false;

        return transitionTo(newState, brokerId, price, qty, message);
    }

    private OrderState mapBrokerStatus(int status) {
        // Fyers status codes:
        // 1 -> Cancelled, 2 -> Filled, 4 -> Transit, 5 -> Rejected, 6 -> Pending
        return switch (status) {
            case 1 -> OrderState.CANCELLED;
            case 2 -> OrderState.FILLED;
            case 4 -> OrderState.SUBMITTED;
            case 5 -> OrderState.REJECTED;
            case 6 -> OrderState.ACCEPTED;
            default -> null;
        };
    }

    // ── Backward compatibility ──────────────────────────────────────────────

    /**
     * Convert this managed order to the existing {@link OrderFill} type.
     */
    public OrderFill toOrderFill() {
        if (state == OrderState.FILLED || state == OrderState.PARTIALLY_FILLED) {
            return OrderFill.success(
                    brokerOrderId != null ? brokerOrderId : clientOrderId,
                    fillPrice, filledQuantity,
                    lastUpdatedAt != null ? lastUpdatedAt : Instant.now());
        }
        return OrderFill.rejected(rejectReason != null ? rejectReason : "Order not filled: " + state);
    }

    // ── Accessors ───────────────────────────────────────────────────────────

    public String getClientOrderId() { return clientOrderId; }
    public String getCorrelationId() { return correlationId; }
    public String getSymbol() { return symbol; }
    public OrderSideType getSide() { return side; }
    public Signal getDirection() { return direction; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public String getStrategyId() { return strategyId; }
    public Instant getCreatedAt() { return createdAt; }
    public OrderState getState() { return state; }
    public String getBrokerOrderId() { return brokerOrderId; }
    public double getFillPrice() { return fillPrice; }
    public int getFilledQuantity() { return filledQuantity; }
    public String getRejectReason() { return rejectReason; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public boolean isTerminal() { return state.isTerminal(); }

    public List<StateTransition> getHistory() {
        stateLock.lock();
        try {
            return List.copyOf(history);
        } finally {
            stateLock.unlock();
        }
    }

    @Override
    public String toString() {
        return String.format("ManagedOrder{id=%s, state=%s, symbol=%s, side=%s, qty=%d/%d}",
                clientOrderId, state, symbol, side, filledQuantity, requestedQuantity);
    }

    // ── Inner record ────────────────────────────────────────────────────────

    public record StateTransition(OrderState from, OrderState to,
                                  Instant timestamp, String detail) {}
}
