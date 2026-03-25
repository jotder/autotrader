package com.rj.engine;

import com.rj.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OrderManagerTest {

    private OrderTracker tracker;
    private OrderManager manager;
    private StubExecutor stubExecutor;

    @BeforeEach
    void setup() {
        tracker = new OrderTracker(Duration.ofSeconds(5));
        stubExecutor = new StubExecutor();
        manager = new OrderManager(stubExecutor, tracker,
                new TradeJournal(ExecutionMode.BACKTEST));
    }

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    void entryHappyPath() {
        TradeSignal signal = testSignal("NSE:SBIN-EQ", Signal.BUY, "corr_1");
        stubExecutor.nextFill = OrderFill.success("PP-1", 100.5, 10, Instant.now());

        OrderFill fill = manager.submitEntry(signal, 10);
        assertTrue(fill.isSuccess());
        assertEquals(100.5, fill.getFillPrice());

        // Order should be in completed (terminal)
        assertEquals(0, tracker.activeCount());
        assertEquals(1, tracker.completedOrders().size());
    }

    @Test
    void exitHappyPath() {
        OpenPosition position = testPosition("NSE:SBIN-EQ", Signal.BUY, "corr_1");
        stubExecutor.nextFill = OrderFill.success("PP-2", 105.0, 10, Instant.now());

        OrderFill fill = manager.submitExit(position,
                PositionMonitor.ExitReason.STOP_LOSS, 104.0);
        assertTrue(fill.isSuccess());
        assertEquals(105.0, fill.getFillPrice());
    }

    // ── Dedup ───────────────────────────────────────────────────────────────

    @Test
    void duplicateEntryBlocked() {
        TradeSignal signal = testSignal("NSE:SBIN-EQ", Signal.BUY, "corr_dup");
        stubExecutor.nextFill = OrderFill.success("PP-3", 100.0, 10, Instant.now());

        // First entry succeeds
        OrderFill fill1 = manager.submitEntry(signal, 10);
        assertTrue(fill1.isSuccess());

        // Second entry with same correlationId is blocked
        OrderFill fill2 = manager.submitEntry(signal, 10);
        assertFalse(fill2.isSuccess());
        assertTrue(fill2.getRejectReason().contains("Duplicate"));
    }

    // ── Executor rejection ──────────────────────────────────────────────────

    @Test
    void executorRejectionRecorded() {
        TradeSignal signal = testSignal("NSE:RELIANCE-EQ", Signal.BUY, "corr_rej");
        stubExecutor.nextFill = OrderFill.rejected("No live price");

        OrderFill fill = manager.submitEntry(signal, 5);
        assertFalse(fill.isSuccess());
        assertEquals("No live price", fill.getRejectReason());

        // Order should still be moved to completed
        assertEquals(0, tracker.activeCount());
    }

    // ── Symbol lock contention ──────────────────────────────────────────────

    @Test
    void symbolLockPreventsContention() throws Exception {
        var latch = new CountDownLatch(1);
        var result1 = new AtomicReference<OrderFill>();
        var result2 = new AtomicReference<OrderFill>();

        // Slow executor — holds lock while "placing" order
        var slowExecutor = new IOrderExecutor() {
            @Override
            public OrderFill placeEntry(TradeSignal signal, int qty) {
                try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return OrderFill.success("SLOW-1", 100.0, qty, Instant.now());
            }
            @Override
            public OrderFill placeExit(OpenPosition p, PositionMonitor.ExitReason r, double price) {
                return OrderFill.success("SLOW-2", price, p.getQuantity(), Instant.now());
            }
        };

        var slowTracker = new OrderTracker(Duration.ofSeconds(30));
        var slowManager = new OrderManager(slowExecutor, slowTracker,
                new TradeJournal(ExecutionMode.BACKTEST));

        TradeSignal sig1 = testSignal("NSE:TEST-EQ", Signal.BUY, "corr_lock1");
        TradeSignal sig2 = testSignal("NSE:TEST-EQ", Signal.BUY, "corr_lock2");

        // Thread 1: acquires symbol lock, waits on latch
        Thread t1 = Thread.ofVirtual().start(() ->
                result1.set(slowManager.submitEntry(sig1, 10)));

        Thread.sleep(50); // Let t1 acquire the lock

        // Thread 2: tries same symbol, should be rejected (tryLock fails)
        Thread t2 = Thread.ofVirtual().start(() ->
                result2.set(slowManager.submitEntry(sig2, 10)));

        Thread.sleep(50); // Let t2 attempt
        latch.countDown(); // Release t1

        t1.join(2000);
        t2.join(2000);

        assertTrue(result1.get().isSuccess());
        assertFalse(result2.get().isSuccess());
        assertTrue(result2.get().getRejectReason().contains("Symbol locked"));

        slowManager.shutdown();
    }

    // ── Exit always goes through ────────────────────────────────────────────

    @Test
    void exitNotBlockedBySymbolLock() {
        // Even if there's an active entry, exit must succeed
        stubExecutor.nextFill = OrderFill.success("PP-EXIT", 105.0, 10, Instant.now());

        OpenPosition pos = testPosition("NSE:SBIN-EQ", Signal.BUY, "corr_exit");
        OrderFill fill = manager.submitExit(pos, PositionMonitor.ExitReason.TAKE_PROFIT, 105.0);
        assertTrue(fill.isSuccess());
    }

    // ── ClientOrderId determinism ───────────────────────────────────────────

    @Test
    void clientOrderIdIsDeterministic() {
        String id1 = OrderManager.generateClientOrderId("corr_X", OrderSideType.ENTRY, 1);
        String id2 = OrderManager.generateClientOrderId("corr_X", OrderSideType.ENTRY, 1);
        assertEquals(id1, id2);
        assertEquals("corr_X_ENTRY_1", id1);
    }

    @Test
    void clientOrderIdDiffersForEntryAndExit() {
        String entry = OrderManager.generateClientOrderId("corr_Y", OrderSideType.ENTRY, 1);
        String exit = OrderManager.generateClientOrderId("corr_Y", OrderSideType.EXIT, 1);
        assertNotEquals(entry, exit);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static TradeSignal testSignal(String symbol, Signal direction, String correlationId) {
        return TradeSignal.builder()
                .symbol(symbol)
                .direction(direction)
                .confidence(0.8)
                .suggestedEntry(100.0)
                .suggestedStopLoss(98.0)
                .suggestedTarget(104.0)
                .strategyId("test-strategy")
                .correlationId(correlationId)
                .build();
    }

    private static OpenPosition testPosition(String symbol, Signal direction, String correlationId) {
        return new OpenPosition(symbol, correlationId, "test-strategy", direction,
                100.0, 10, 98.0, 104.0, Instant.now());
    }

    /** Stub executor that returns a pre-configured fill. */
    private static class StubExecutor implements IOrderExecutor {
        OrderFill nextFill = OrderFill.rejected("Not configured");

        @Override
        public OrderFill placeEntry(TradeSignal signal, int quantity) {
            return nextFill;
        }

        @Override
        public OrderFill placeExit(OpenPosition position,
                                   PositionMonitor.ExitReason reason, double exitPrice) {
            return nextFill;
        }
    }
}
