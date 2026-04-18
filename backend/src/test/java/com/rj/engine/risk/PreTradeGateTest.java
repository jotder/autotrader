package com.rj.engine.risk;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyRiskConfig;
import com.rj.config.TradeStrategyConfig;
import com.rj.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreTradeGateTest {

    private static final double CAPITAL = 500_000.0;
    private static final String STRAT = "trend_following";

    private static final Supplier<ZonedDateTime> MARKET_HOURS =
            () -> ZonedDateTime.of(2026, 3, 28, 10, 30, 0, 0, ZoneId.of("Asia/Kolkata"));

    @Mock
    private RiskSessionState riskState;

    private RiskConfig riskConfig;
    private PreTradeGate gate;

    @BeforeEach
    void setUp() {
        riskConfig = RiskConfig.defaults();
        gate = new PreTradeGate(riskConfig, riskState, MARKET_HOURS);
        // Register strategy config so Gate 7/8 passes
        TradeStrategyConfig stratCfg = new TradeStrategyConfig();
        stratCfg.setStrategyId(STRAT);
        stratCfg.setName("Trend Following");
        stratCfg.setActive(true);
        stratCfg.setAllocationPercentage(100.0);
        stratCfg.setSizingType(com.rj.model.SizingType.VOLATILITY_ATR);
        stratCfg.setRiskPercentage(2.0);
        stratCfg.setAtrMultiplier(2.0);
        gate.updateStrategyConfig(stratCfg);
    }

    @Test
    void gate1_killSwitch_rejects() {
        when(riskState.isKillSwitchActive()).thenReturn(true);
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("Kill switch"));
    }

    @Test
    void gate2_drawdownDoubleCheck_rejects() {
        when(riskState.isKillSwitchActive()).thenReturn(false);
        when(riskState.checkDrawdownAndActivate()).thenReturn(true);
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("Drawdown"));
    }

    @Test
    void gate3_dailyProfitLock_rejects() {
        when(riskState.isKillSwitchActive()).thenReturn(false);
        when(riskState.checkDrawdownAndActivate()).thenReturn(false);
        when(riskState.isDailyProfitLocked()).thenReturn(true);
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("Daily profit target"));
    }

    @Test
    void gate4_dailyLossLimit_rejects() {
        when(riskState.isKillSwitchActive()).thenReturn(false);
        when(riskState.checkDrawdownAndActivate()).thenReturn(false);
        when(riskState.isDailyProfitLocked()).thenReturn(false);
        when(riskState.getDailyRealizedPnl()).thenReturn(-riskConfig.getMaxDailyLossInr() - 1);
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("Daily loss limit"));
    }

    @Test
    void gate6_consecutiveLossLimit_rejects() {
        stubAllClear();
        when(riskState.getStrategyRiskOverride(STRAT)).thenReturn(null);
        when(riskState.getConsecutiveLosses(STRAT))
                .thenReturn(riskConfig.getMaxConsecutiveLossesPerStrategy());
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("consecutive losses"));
    }

    @Test
    void gate7_strategyConfigMissing_rejects() {
        stubAllClear();
        when(riskState.getConsecutiveLosses(STRAT)).thenReturn(0);
        when(riskState.getStrategyRiskOverride(STRAT)).thenReturn(null);
        // Gate without registered strategy
        PreTradeGate gateNoConfig = new PreTradeGate(riskConfig, riskState, MARKET_HOURS);
        PreTradeResult r = gateNoConfig.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("Strategy config not found"));
    }

    @Test
    void allGatesPass_returnsApprovedWithPositiveQuantity() {
        stubAllClear();
        when(riskState.getConsecutiveLosses(STRAT)).thenReturn(0);
        when(riskState.getStrategyRiskOverride(STRAT)).thenReturn(null);
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertTrue(r.approved());
        assertTrue(r.quantity() > 0);
    }

    @Test
    void applyStrategyRiskOverride_delegatesToRiskSessionState() {
        StrategyRiskConfig override = new StrategyRiskConfig(2.0, 2.0, 2.0, 1.0, 1.0, 20.0, 100, 3);
        gate.applyStrategyRiskOverride(STRAT, override);
        verify(riskState).applyStrategyRiskOverride(STRAT, override);
    }

    @Test
    void removeStrategyRiskOverride_delegatesToRiskSessionState() {
        gate.removeStrategyRiskOverride(STRAT);
        verify(riskState).removeStrategyRiskOverride(STRAT);
    }

    private void stubAllClear() {
        when(riskState.isKillSwitchActive()).thenReturn(false);
        when(riskState.checkDrawdownAndActivate()).thenReturn(false);
        when(riskState.isDailyProfitLocked()).thenReturn(false);
        when(riskState.getDailyRealizedPnl()).thenReturn(0.0);
    }

    private TradeSignal buildSignal(double entry, double sl) {
        return TradeSignal.builder()
                .symbol("NSE:SBIN-EQ")
                .correlationId("corr-" + System.nanoTime())
                .direction(Signal.BUY)
                .confidence(0.9)
                .strategyId(STRAT)
                .suggestedEntry(entry)
                .suggestedStopLoss(sl)
                .suggestedTarget(entry * 1.10)
                .build();
    }
}
