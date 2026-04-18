package com.rj.engine.risk;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyRiskConfig;
import com.rj.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RiskSessionStateTest {

    private RiskSessionState state;
    private RiskConfig riskConfig;

    @BeforeEach
    void setUp() {
        riskConfig = RiskConfig.fromEnvironment(key -> switch (key) {
            case "RISK_INITIAL_CAPITAL_INR" -> "100000";
            case "RISK_MAX_DAILY_LOSS_INR"   -> "5000";
            case "RISK_MAX_DAILY_PROFIT_INR" -> "20000";
            case "RISK_MAX_DRAWDOWN_PCT"      -> "3.0";
            case "RISK_MAX_CONSECUTIVE_LOSSES" -> "5";
            default -> null;
        });
        state = new RiskSessionState(riskConfig);
    }

    @Test
    void recordClosedTrade_losingTrade_incrementsConsecutiveLoss() {
        TradeRecord loss = buildTrade("strat-1", false);
        state.recordClosedTrade(loss);
        assertEquals(1, state.getConsecutiveLosses("strat-1"));
    }

    @Test
    void recordClosedTrade_winningTrade_resetsConsecutiveLossCounter() {
        state.recordClosedTrade(buildTrade("strat-1", false));
        state.recordClosedTrade(buildTrade("strat-1", false));
        state.recordClosedTrade(buildTrade("strat-1", true));
        assertEquals(0, state.getConsecutiveLosses("strat-1"));
    }

    @Test
    void recordClosedTrade_reachingProfitTarget_locksDailyProfit() {
        // PnL required: > 20000 INR
        TradeRecord bigWin = buildTradeWithPnl("strat-1", 21000.0);
        state.recordClosedTrade(bigWin);
        assertTrue(state.isDailyProfitLocked());
    }

    @Test
    void updateCurrentEquity_drawdownExceedsLimit_activatesKillSwitch() {
        // Initial capital = 100000, max drawdown = 3% = 3000 INR loss
        state.updateCurrentEquity(-4000); // 4% loss → exceeds 3%
        assertTrue(state.isKillSwitchActive());
    }

    @Test
    void triggerAnomaly_setsAnomalyModeAndKillSwitch() {
        state.triggerAnomaly("Test anomaly");
        assertTrue(state.isAnomalyMode());
        assertTrue(state.isKillSwitchActive());
        assertEquals("Test anomaly", state.getAnomalyReason());
    }

    @Test
    void acknowledgeAnomaly_clearsAnomalyMode() {
        state.triggerAnomaly("Test");
        boolean cleared = state.acknowledgeAnomaly();
        assertTrue(cleared);
        assertFalse(state.isAnomalyMode());
        assertNull(state.getAnomalyReason());
    }

    @Test
    void applyStrategyRiskOverride_nullOverride_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> state.applyStrategyRiskOverride("strat-1", null));
    }

    @Test
    void resetDay_clearsCounters() {
        state.recordClosedTrade(buildTrade("strat-1", false));
        state.activateKillSwitch("test");
        state.resetDay();
        assertFalse(state.isKillSwitchActive());
        assertEquals(0, state.getConsecutiveLosses("strat-1"));
    }

    private TradeRecord buildTrade(String strategyId, boolean winner) {
        return buildTradeWithPnl(strategyId, winner ? 500.0 : -500.0);
    }

    private TradeRecord buildTradeWithPnl(String strategyId, double pnl) {
        Map<Timeframe, Signal> votes = new EnumMap<>(Timeframe.class);
        // entry=100, qty=10; exitPrice derived so that (exit-entry)*qty == pnl
        double entryPrice = 100.0;
        int qty = 10;
        double exitPrice = entryPrice + (pnl / qty); // BUY direction
        TradeRecord tr = new TradeRecord(
                "corr-" + System.nanoTime(), "NSE:SBIN-EQ", strategyId,
                ExecutionMode.PAPER, Signal.BUY,
                entryPrice, qty, 95.0, entryPrice + 10,
                Instant.now(), 1.5, 0.9, votes);
        com.rj.engine.ExitReason reason = pnl >= 0
                ? com.rj.engine.ExitReason.TAKE_PROFIT
                : com.rj.engine.ExitReason.STOP_LOSS;
        tr.close(exitPrice, Instant.now(), reason);
        return tr;
    }
}
