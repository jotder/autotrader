package com.rj.engine;

import com.rj.model.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManagedOrderTest {

    private final OrderTracker tracker = new OrderTracker(Duration.ofSeconds(30));

    private ManagedOrder createTestOrder() {
        return new ManagedOrder("test-id", "corr-1", "NSE:SBIN-EQ",
                OrderSideType.ENTRY, Signal.BUY, 10, "intraday", tracker);
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
    void brokerUpdatePath() {
        var order = createTestOrder();
        assertTrue(order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null));
        
        // Fyers status 2 -> Filled
        assertTrue(order.updateFromBroker(2, "BRK-002", 100.5, 10, "Success"));
        assertEquals(OrderState.FILLED, order.getState());
        assertTrue(order.isTerminal());
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
    void invalidTransitions() {
        var order = createTestOrder();
        // Skip submitted
        assertFalse(order.transitionTo(OrderState.FILLED, null, 100, 10, null));
        
        order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null);
        order.transitionTo(OrderState.REJECTED, null, 0, 0, "Fail");
        
        // From terminal
        assertFalse(order.transitionTo(OrderState.ACCEPTED, "BRK", 0, 0, null));
    }

    @Test
    void toOrderFill() {
        var order = createTestOrder();
        order.transitionTo(OrderState.SUBMITTED, null, 0, 0, null);
        order.transitionTo(OrderState.FILLED, "BRK-X", 250.0, 10, null);

        OrderFill fill = order.toOrderFill();
        assertTrue(fill.isSuccess());
        assertEquals("BRK-X", fill.getOrderId());
        assertEquals(250.0, fill.getFillPrice());
    }
}
