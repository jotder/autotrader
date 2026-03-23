package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyRiskConfig;
import com.rj.model.ExecutionMode;
import com.rj.model.OpenPosition;
import com.rj.model.Signal;
import com.rj.model.Timeframe;
import com.rj.model.TradeRecord;
import com.rj.model.TradeSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AT-002: per-strategy risk config overrides in RiskManager.
 */
class RiskManagerStrategyOverrideTest {

    private static final String STRATEGY_ID = "trend_following";
    private static final double CAPITAL = 500_000.0;

    private RiskManager riskManager;

    @BeforeEach
    void setUp() {
        riskManager = new RiskManager(RiskConfig.defaults());
    }

    // ── applyStrategyRiskOverride ─────────────────────────────────────────────

    @Test
    void applyStrategyRiskOverride_setsOverrideUsedInPreTradeCheck() {
        // Override: risk_per_trade_pct = 1.0% (very tight)
        StrategyRiskConfig override = new StrategyRiskConfig(1.0, 2.0, 2.0, 1.0, 1.0, 20.0, 1000, 3);
        riskManager.applyStrategyRiskOverride(STRATEGY_ID, override);

        TradeSignal signal = buildSignal(STRATEGY_ID, 100.0, 95.0, 110.0); // 5 INR SL
        RiskManager.PreTradeResult result = riskManager.preTradeCheck(signal, Collections.emptyList(), CAPITAL);

        assertTrue(result.approved());
        // riskBudget = 500000 * (1.0/100) = 5000; riskPerUnit = 5; rawQty = 5000/5 = 1000
        assertEquals(1000, result.quantity());
    }

    @Test
    void applyStrategyRiskOverride_higherRiskPct_producesMoreQuantity() {
        // entry=100, SL=50 → riskPerUnit=50 (large SL keeps rawQty below maxQty cap)
        // Global 2%: riskBudget=10000; rawQty=200; exposureCap=500000*0.20/100=1000 → qty=200
        TradeSignal signal = buildSignal(STRATEGY_ID, 100.0, 50.0, 200.0);
        RiskManager.PreTradeResult withGlobal = riskManager.preTradeCheck(signal, Collections.emptyList(), CAPITAL);

        // Override risk to 4%: riskBudget=20000; rawQty=400; exposureCap=1000 → qty=400
        riskManager.applyStrategyRiskOverride(STRATEGY_ID,
                new StrategyRiskConfig(4.0, 2.0, 2.0, 1.0, 1.0, 20.0, 1000, 3));
        RiskManager.PreTradeResult withOverride = riskManager.preTradeCheck(signal, Collections.emptyList(), CAPITAL);

        assertTrue(withGlobal.approved());
        assertTrue(withOverride.approved());
        assertTrue(withOverride.quantity() > withGlobal.quantity(),
                "Higher risk % should produce more quantity");
    }

    @Test
    void applyStrategyRiskOverride_maxQtyCap_respected() {
        // Override with very small max_qty = 10
        riskManager.applyStrategyRiskOverride(STRATEGY_ID,
                new StrategyRiskConfig(2.0, 2.0, 2.0, 1.0, 1.0, 20.0, 10, 3));

        TradeSignal signal = buildSignal(STRATEGY_ID, 100.0, 99.0, 102.0); // SL=1, riskBudget=10000 → rawQty=10000
        RiskManager.PreTradeResult result = riskManager.preTradeCheck(signal, Collections.emptyList(), CAPITAL);

        assertTrue(result.approved());
        assertTrue(result.quantity() <= 10, "Quantity must be capped at override maxQty=10");
    }

    @Test
    void applyStrategyRiskOverride_maxExposurePct_respected() {
        // Override max_exposure_pct = 1% of capital (very tight: 5000 INR max)
        riskManager.applyStrategyRiskOverride(STRATEGY_ID,
                new StrategyRiskConfig(2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1000, 3));

        // Simulate existing exposure = 4500 INR on same symbol
        OpenPosition existing = new OpenPosition(
                "NSE:SBIN-EQ", "corr-1", STRATEGY_ID, Signal.BUY,
                90.0, 50, 85.0, 100.0, java.time.Instant.now());
        // exposure = 90 * 50 = 4500

        TradeSignal signal = buildSignal(STRATEGY_ID, "NSE:SBIN-EQ", 100.0, 95.0, 110.0);
        RiskManager.PreTradeResult result = riskManager.preTradeCheck(signal, List.of(existing), CAPITAL);

        // maxExposure = 500000 * (1.0/100) = 5000; currentExposure = 4500; gap = 500
        // exposureCapQty = floor(500 / 100) = 5
        assertTrue(result.approved());
        assertTrue(result.quantity() <= 5, "Quantity must be capped by exposure gap");
    }

    @Test
    void applyStrategyRiskOverride_nullOverride_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> riskManager.applyStrategyRiskOverride(STRATEGY_ID, null));
    }

    // ── removeStrategyRiskOverride ────────────────────────────────────────────

    @Test
    void removeStrategyRiskOverride_reverts_toGlobalConfig() {
        // Apply a very restrictive override (maxQty = 5)
        riskManager.applyStrategyRiskOverride(STRATEGY_ID,
                new StrategyRiskConfig(2.0, 2.0, 2.0, 1.0, 1.0, 20.0, 5, 3));

        TradeSignal signal = buildSignal(STRATEGY_ID, 100.0, 99.0, 102.0);
        RiskManager.PreTradeResult withOverride = riskManager.preTradeCheck(signal, Collections.emptyList(), CAPITAL);
        assertEquals(5, withOverride.quantity());

        // Remove override — now should use global maxQty = 1000
        riskManager.removeStrategyRiskOverride(STRATEGY_ID);
        RiskManager.PreTradeResult withoutOverride = riskManager.preTradeCheck(signal, Collections.emptyList(), CAPITAL);
        assertTrue(withoutOverride.quantity() > 5,
                "After removing override, quantity should revert to global config");
    }

    // ── Per-strategy consecutive losses ───────────────────────────────────────

    @Test
    void consecutiveLossLimit_fromOverride_suspendsStrategyEarlier() {
        // Override: max_consecutive_losses = 1 (suspend after 1 loss)
        riskManager.applyStrategyRiskOverride(STRATEGY_ID,
                new StrategyRiskConfig(2.0, 2.0, 2.0, 1.0, 1.0, 20.0, 1000, 1));

        // Record one losing trade
        com.rj.model.TradeRecord loss = buildLossTrade(STRATEGY_ID);
        riskManager.recordClosedTrade(loss);

        // Now preTradeCheck should reject
        TradeSignal signal = buildSignal(STRATEGY_ID, 100.0, 95.0, 110.0);
        RiskManager.PreTradeResult result = riskManager.preTradeCheck(signal, Collections.emptyList(), CAPITAL);

        assertFalse(result.approved());
        assertTrue(result.rejectReason().contains("consecutive losses"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TradeSignal buildSignal(String strategyId, double entry, double sl, double tp) {
        return buildSignal(strategyId, "NSE:SBIN-EQ", entry, sl, tp);
    }

    private TradeSignal buildSignal(String strategyId, String symbol, double entry, double sl, double tp) {
        return TradeSignal.builder()
                .symbol(symbol)
                .correlationId("corr-" + System.nanoTime())
                .direction(Signal.BUY)
                .confidence(0.9)
                .strategyId(strategyId)
                .suggestedEntry(entry)
                .suggestedStopLoss(sl)
                .suggestedTarget(tp)
                .build();
    }

    private TradeRecord buildLossTrade(String strategyId) {
        Map<Timeframe, Signal> votes = new EnumMap<>(Timeframe.class);
        TradeRecord tr = new TradeRecord(
                "corr-loss", "NSE:SBIN-EQ", strategyId,
                ExecutionMode.PAPER, Signal.BUY,
                100.0, 10, 95.0, 110.0,
                Instant.now(), 1.5, 0.9, votes);
        tr.close(95.0, Instant.now(), PositionMonitor.ExitReason.STOP_LOSS);
        return tr;
    }
}
