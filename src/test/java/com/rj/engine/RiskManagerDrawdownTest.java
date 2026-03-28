package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.ExecutionMode;
import com.rj.model.Signal;
import com.rj.model.TradeRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RiskManagerDrawdownTest {

    private RiskConfig riskConfig;
    private RiskManager riskManager;

    @BeforeEach
    void setup() {
        Map<String, String> env = new HashMap<>();
        env.put("RISK_INITIAL_CAPITAL_INR", "100000");
        env.put("RISK_MAX_DRAWDOWN_PCT", "3.0");
        env.put("RISK_MAX_DAILY_LOSS_INR", "10000");
        riskConfig = RiskConfig.fromEnvironment(env::get);
        riskManager = new RiskManager(riskConfig);
    }

    @Test
    void peakEquityIsInitialized() {
        assertEquals(100000.0, riskConfig.getInitialCapitalInr());
    }

    @Test
    void drawdownBreachOnRealizedLoss() {
        // Initial 100,000. 3% drawdown = 3,000 loss.
        TradeRecord lossTrade = new TradeRecord(
                "c1", "NSE:SBIN-EQ", "s1", ExecutionMode.PAPER,
                Signal.BUY, 500.0, 10, 490.0, 550.0,
                Instant.now(), 2.0, 1.0, Map.of());
        
        // 3500 loss (3.5%)
        lossTrade.close(500.0 - 350.0, Instant.now(), PositionMonitor.ExitReason.STOP_LOSS);
        
        riskManager.recordClosedTrade(lossTrade);
        
        assertTrue(riskManager.isKillSwitchActive());
        // preTradeCheck should reject
        var result = riskManager.preTradeCheck(null, java.util.List.of(), 100000);
        assertFalse(result.approved());
        assertTrue(result.rejectReason().contains("Drawdown") || result.rejectReason().contains("Kill switch"));
    }

    @Test
    void drawdownBreachOnOpenPnL() {
        // Initial 100,000. 3% drawdown = 3,000.
        // Current open PnL = -3500.
        riskManager.updateCurrentEquity(-3500.0);
        
        assertTrue(riskManager.isKillSwitchActive());
        assertTrue(riskManager.isAnomalyMode());
        assertEquals("3% Drawdown Breached (Trailing)", riskManager.getAnomalyReason());
    }

    @Test
    void trailingDrawdownTracksPeak() {
        // 1. Gain 5000 -> Equity 105,000. New Peak = 105,000.
        TradeRecord winTrade = new TradeRecord(
                "c1", "NSE:SBIN-EQ", "s1", ExecutionMode.PAPER,
                Signal.BUY, 500.0, 10, 490.0, 550.0,
                Instant.now(), 2.0, 1.0, Map.of());
        winTrade.close(500.0 + 500.0, Instant.now(), PositionMonitor.ExitReason.TAKE_PROFIT);
        riskManager.recordClosedTrade(winTrade);
        
        assertFalse(riskManager.isKillSwitchActive());
        
        // 2. Open loss of 3000. Equity 102,000.
        // Drawdown from peak (105,000) = (105,000 - 102,000) / 105,000 = 2.85% (Still OK)
        riskManager.updateCurrentEquity(-3000.0);
        assertFalse(riskManager.isKillSwitchActive());
        
        // 3. Open loss of 3200. Equity 101,800.
        // Drawdown = (105,000 - 101,800) / 105,000 = 3.04% (Breach!)
        riskManager.updateCurrentEquity(-3200.0);
        assertTrue(riskManager.isKillSwitchActive());
    }
}
