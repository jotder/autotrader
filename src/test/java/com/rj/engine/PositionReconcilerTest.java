package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.*;
import com.rj.model.Timeframe;
import fyers.FyersPositions;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PositionReconciler}.
 * All broker calls are mocked via {@link MockFyersPositions} — no real API calls.
 */
class PositionReconcilerTest {

    private MockFyersPositions mockPositions;
    private PositionMonitor positionMonitor;
    private ConcurrentHashMap<String, TradeRecord> openRecords;
    private TradeJournal journal;
    private RiskConfig riskConfig;
    private PositionReconciler reconciler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mockPositions = new MockFyersPositions();
        riskConfig = RiskConfig.defaults();
        positionMonitor = new PositionMonitor(TickStore.getInstance(), riskConfig, (p, r) -> {}, null);
        openRecords = new ConcurrentHashMap<>();
        journal = new TradeJournal(ExecutionMode.LIVE, tempDir);
        reconciler = new PositionReconciler(mockPositions, positionMonitor, openRecords, journal, riskConfig);
    }

    @Test
    void emptyBrokerAndEngine_noOp() {
        mockPositions.setPositions(buildPositionsSummary()); // empty

        var result = reconciler.reconcile();

        assertEquals(0, result.adopted());
        assertEquals(0, result.removed());
        assertEquals(0, result.matched());
        assertEquals(0, result.qtyMismatch());
        assertTrue(result.details().isEmpty());
    }

    @Test
    void brokerHasOnePosition_engineEmpty_adopts() {
        mockPositions.setPositions(buildPositionsSummary(
                netPosition("NSE:SBIN-EQ", 100, 550.0, 555.0)
        ));

        var result = reconciler.reconcile();

        assertEquals(1, result.adopted());
        assertEquals(0, result.removed());
        assertEquals(0, result.matched());
        assertEquals(1, positionMonitor.openPositionCount());
        assertEquals(1, openRecords.size());

        // Verify adopted position has correct properties
        OpenPosition adopted = positionMonitor.openPositions().iterator().next();
        assertEquals("NSE:SBIN-EQ", adopted.getSymbol());
        assertEquals(Signal.BUY, adopted.getDirection());
        assertEquals(100, adopted.getQuantity());
        assertEquals(550.0, adopted.getEntryPrice(), 0.001);
        // SL should be 2% below entry for LONG
        assertEquals(550.0 * 0.98, adopted.getCurrentStopLoss(), 0.01);
        // No TP
        assertEquals(0, adopted.getTakeProfit(), 0.001);
    }

    @Test
    void brokerHasLongAndShort_engineEmpty_adoptsBoth() {
        mockPositions.setPositions(buildPositionsSummary(
                netPosition("NSE:SBIN-EQ", 100, 550.0, 555.0),
                netPosition("NSE:RELIANCE-EQ", -50, 2500.0, 2480.0)
        ));

        var result = reconciler.reconcile();

        assertEquals(2, result.adopted());
        assertEquals(2, positionMonitor.openPositionCount());
        assertEquals(2, openRecords.size());

        // Check short position
        boolean hasShort = positionMonitor.openPositions().stream()
                .anyMatch(p -> p.getSymbol().equals("NSE:RELIANCE-EQ")
                        && p.getDirection() == Signal.SELL
                        && p.getQuantity() == 50);
        assertTrue(hasShort, "Should have adopted SHORT position for RELIANCE");
    }

    @Test
    void brokerApiReturnsNull_throwsException() {
        mockPositions.setPositions(null);

        assertThrows(PositionReconciler.ReconciliationException.class, () -> reconciler.reconcile());
    }

    @Test
    void brokerPositionWithZeroQty_skipped() {
        mockPositions.setPositions(buildPositionsSummary(
                netPosition("NSE:SBIN-EQ", 0, 550.0, 555.0) // closed position
        ));

        var result = reconciler.reconcile();

        assertEquals(0, result.adopted());
        assertEquals(0, positionMonitor.openPositionCount());
    }

    @Test
    void matchingPositionExists_verified() {
        // Pre-register a position in the engine
        OpenPosition existing = new OpenPosition(
                "NSE:SBIN-EQ", "corr-1", "strat-1", Signal.BUY,
                550.0, 100, 539.0, 572.0, java.time.Instant.now());
        positionMonitor.addPosition(existing);

        // Broker also has it with same qty
        mockPositions.setPositions(buildPositionsSummary(
                netPosition("NSE:SBIN-EQ", 100, 550.0, 555.0)
        ));

        var result = reconciler.reconcile();

        assertEquals(0, result.adopted());
        assertEquals(0, result.removed());
        assertEquals(1, result.matched());
        assertEquals(0, result.qtyMismatch());
    }

    @Test
    void matchingPositionWithQtyMismatch_logged() {
        // Engine has 100, broker has 150
        OpenPosition existing = new OpenPosition(
                "NSE:SBIN-EQ", "corr-1", "strat-1", Signal.BUY,
                550.0, 100, 539.0, 572.0, java.time.Instant.now());
        positionMonitor.addPosition(existing);

        mockPositions.setPositions(buildPositionsSummary(
                netPosition("NSE:SBIN-EQ", 150, 550.0, 555.0)
        ));

        var result = reconciler.reconcile();

        assertEquals(0, result.adopted());
        assertEquals(1, result.qtyMismatch());
        assertTrue(result.details().get(0).contains("QTY_MISMATCH"));
    }

    @Test
    void staleEnginePosition_removed() {
        // Engine has a position that broker doesn't
        OpenPosition stale = new OpenPosition(
                "NSE:INFY-EQ", "corr-stale", "strat-1", Signal.BUY,
                1500.0, 50, 1470.0, 1560.0, java.time.Instant.now());
        positionMonitor.addPosition(stale);
        java.util.Map<Timeframe, Signal> votes = new java.util.EnumMap<>(Timeframe.class);
        votes.put(Timeframe.M5, Signal.BUY);
        openRecords.put("corr-stale", new TradeRecord(
                "corr-stale", "NSE:INFY-EQ", "strat-1", ExecutionMode.LIVE,
                Signal.BUY, 1500.0, 50, 1470.0, 1560.0,
                java.time.Instant.now(), 0, 0, votes));

        // Broker has no open positions
        mockPositions.setPositions(buildPositionsSummary());

        var result = reconciler.reconcile();

        assertEquals(0, result.adopted());
        assertEquals(1, result.removed());
        assertEquals(0, positionMonitor.openPositionCount());
        assertEquals(0, openRecords.size());
        assertTrue(result.details().get(0).contains("REMOVED_STALE"));
    }

    @Test
    void lastResultAccessible() {
        mockPositions.setPositions(buildPositionsSummary());
        assertNull(reconciler.getLastResult());

        reconciler.reconcile();

        assertNotNull(reconciler.getLastResult());
    }

    // ── Test helpers ────────────────────────────────────────────────────────────

    /** Mock FyersPositions that returns a pre-configured PositionsSummary. */
    static class MockFyersPositions extends FyersPositions {
        private PositionsSummary positions;

        MockFyersPositions() {
            // Don't call super() broker init — we override getPositions()
        }

        void setPositions(PositionsSummary positions) {
            this.positions = positions;
        }

        @Override
        public PositionsSummary getPositions() {
            return positions;
        }
    }

    private static PositionsSummary buildPositionsSummary(JSONObject... positions) {
        JSONObject json = new JSONObject();
        JSONArray arr = new JSONArray();
        for (JSONObject p : positions) {
            arr.put(p);
        }
        json.put("netPositions", arr);
        json.put("overall", new JSONObject()
                .put("count_open", positions.length)
                .put("count_total", positions.length)
                .put("pl_realized", 0.0)
                .put("pl_unrealized", 0.0)
                .put("pl_total", 0.0));
        return PositionsSummary.from(json);
    }

    private static JSONObject netPosition(String symbol, int netQty, double netAvg, double ltp) {
        return new JSONObject()
                .put("symbol", symbol)
                .put("id", symbol + "-INTRADAY")
                .put("productType", "INTRADAY")
                .put("fyToken", "")
                .put("crossCurrency", "")
                .put("segment", 11)
                .put("exchange", 10)
                .put("side", netQty > 0 ? 1 : (netQty < 0 ? -1 : 0))
                .put("netQty", netQty)
                .put("buyQty", Math.max(netQty, 0))
                .put("sellQty", Math.max(-netQty, 0))
                .put("dayBuyQty", 0)
                .put("daySellQty", 0)
                .put("cfBuyQty", 0)
                .put("cfSellQty", 0)
                .put("qty", Math.abs(netQty))
                .put("qtyMulti_com", 1)
                .put("slNo", 1)
                .put("buyAvg", netAvg)
                .put("sellAvg", 0.0)
                .put("netAvg", netAvg)
                .put("buyVal", netAvg * Math.abs(netQty))
                .put("sellVal", 0.0)
                .put("ltp", ltp)
                .put("pl", 0.0)
                .put("realized_profit", 0.0)
                .put("unrealized_profit", (ltp - netAvg) * netQty)
                .put("rbiRefRate", 1.0);
    }
}
