package com.rj.engine;

import com.rj.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManagedOrderTest {

    private ManagedOrder createTestOrder() {
        return new ManagedOrder("test-id", "corr-1", "NSE:SBIN-EQ",
                OrderSideType.ENTRY, Signal.BUY, 10, "intraday");
    }

    @Test
    void initialStateIsCreated() {
        var order = createTestOrder();
        assertEquals(OrderState.CREATED, order.getState());
        assertFalse(order.isTerminal());
    }

    @Test
    void happyPathEntry() {
        var order = createTestOrder();
        assertTrue(order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null));
        assertTrue(order.transitionTo(OrderState.ACCEPTED, "BRK-001", 0, 0, null));
        assertTrue(order.transitionTo(OrderState.FILLED, "BRK-001", 100.5, 10, null));

        assertEquals(OrderState.FILLED, order.getState());
        assertTrue(order.isTerminal());
        assertEquals("BRK-001", order.getBrokerOrderId());
        assertEquals(100.5, order.getFillPrice());
        assertEquals(10, order.getFilledQuantity());
    }

    @Test
    void rejectionPath() {
        var order = createTestOrder();
        assertTrue(order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null));
        assertTrue(order.transitionTo(OrderState.REJECTED, null, 0, 0, "Insufficient margin"));

        assertEquals(OrderState.REJECTED, order.getState());
        assertTrue(order.isTerminal());
        assertEquals("Insufficient margin", order.getRejectReason());
    }

    @Test
    void expiredPath() {
        var order = createTestOrder();
        assertTrue(order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null));
        assertTrue(order.transitionTo(OrderState.EXPIRED, null, 0, 0, "Timed out"));

        assertEquals(OrderState.EXPIRED, order.getState());
        assertTrue(order.isTerminal());
    }

    @Test
    void partialFillThenFull() {
        var order = createTestOrder();
        order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null);
        order.transitionTo(OrderState.ACCEPTED, "BRK-002", 0, 0, null);
        assertTrue(order.transitionTo(OrderState.PARTIALLY_FILLED, "BRK-002", 100.0, 5, null));
        assertEquals(OrderState.PARTIALLY_FILLED, order.getState());
        assertFalse(order.isTerminal());

        assertTrue(order.transitionTo(OrderState.FILLED, "BRK-002", 100.2, 10, null));
        assertTrue(order.isTerminal());
    }

    @Test
    void partialFillThenCancelled() {
        var order = createTestOrder();
        order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null);
        order.transitionTo(OrderState.ACCEPTED, "BRK-003", 0, 0, null);
        order.transitionTo(OrderState.PARTIALLY_FILLED, "BRK-003", 100.0, 3, null);
        assertTrue(order.transitionTo(OrderState.CANCELLED, null, 0, 0, "Manual cancel"));

        assertEquals(OrderState.CANCELLED, order.getState());
        assertTrue(order.isTerminal());
    }

    // ── Invalid transitions ─────────────────────────────────────────────────

    @Test
    void cannotSkipSubmitted() {
        var order = createTestOrder();
        assertFalse(order.transitionTo(OrderState.FILLED, null, 100, 10, null));
        assertEquals(OrderState.CREATED, order.getState());
    }

    @Test
    void cannotTransitionFromTerminal() {
        var order = createTestOrder();
        order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null);
        order.transitionTo(OrderState.REJECTED, null, 0, 0, "No margin");

        assertFalse(order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null));
        assertEquals(OrderState.REJECTED, order.getState());
    }

    @Test
    void cannotGoFromAcceptedToSubmitted() {
        var order = createTestOrder();
        order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null);
        order.transitionTo(OrderState.ACCEPTED, "BRK", 0, 0, null);

        assertFalse(order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null));
    }

    // ── History ─────────────────────────────────────────────────────────────

    @Test
    void historyRecordsAllTransitions() {
        var order = createTestOrder();
        order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null);
        order.transitionTo(OrderState.ACCEPTED, "BRK", 0, 0, null);
        order.transitionTo(OrderState.FILLED, "BRK", 100.0, 10, null);

        List<ManagedOrder.StateTransition> history = order.getHistory();
        assertEquals(3, history.size());
        assertEquals(OrderState.CREATED, history.get(0).from());
        assertEquals(OrderState.SUBMITTED, history.get(0).to());
        assertEquals(OrderState.SUBMITTED, history.get(1).from());
        assertEquals(OrderState.ACCEPTED, history.get(1).to());
        assertEquals(OrderState.ACCEPTED, history.get(2).from());
        assertEquals(OrderState.FILLED, history.get(2).to());
    }

    // ── toOrderFill ─────────────────────────────────────────────────────────

    @Test
    void toOrderFillOnFilled() {
        var order = createTestOrder();
        order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null);
        order.transitionTo(OrderState.ACCEPTED, "BRK-X", 0, 0, null);
        order.transitionTo(OrderState.FILLED, "BRK-X", 250.0, 10, null);

        OrderFill fill = order.toOrderFill();
        assertTrue(fill.isSuccess());
        assertEquals("BRK-X", fill.getOrderId());
        assertEquals(250.0, fill.getFillPrice());
        assertEquals(10, fill.getFillQuantity());
    }

    @Test
    void toOrderFillOnRejected() {
        var order = createTestOrder();
        order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null);
        order.transitionTo(OrderState.REJECTED, null, 0, 0, "Bad request");

        OrderFill fill = order.toOrderFill();
        assertFalse(fill.isSuccess());
        assertEquals("Bad request", fill.getRejectReason());
    }

    // ── Identity ────────────────────────────────────────────────────────────

    @Test
    void identityFieldsAreImmutable() {
        var order = createTestOrder();
        assertEquals("test-id", order.getClientOrderId());
        assertEquals("corr-1", order.getCorrelationId());
        assertEquals("NSE:SBIN-EQ", order.getSymbol());
        assertEquals(OrderSideType.ENTRY, order.getSide());
        assertEquals(Signal.BUY, order.getDirection());
        assertEquals(10, order.getRequestedQuantity());
        assertEquals("intraday", order.getStrategyId());
    }
}
