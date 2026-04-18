package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.config.TradeStrategyConfig;
import com.rj.engine.risk.PreTradeGate;
import com.rj.engine.risk.PreTradeResult;
import com.rj.engine.risk.RiskSessionState;
import com.rj.model.*;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class FnoRiskSizingTest {

    private static final Supplier<ZonedDateTime> MARKET_HOURS_CLOCK =
            () -> ZonedDateTime.of(2026, 3, 28, 10, 30, 0, 0, ZoneId.of("Asia/Kolkata"));

    private static PreTradeGate gateFor(RiskConfig cfg) {
        RiskSessionState state = new RiskSessionState(cfg);
        PreTradeGate gate = new PreTradeGate(cfg, state, MARKET_HOURS_CLOCK);
        // Register "test" strategy config so Gate 8 passes
        TradeStrategyConfig stratCfg = new TradeStrategyConfig();
        stratCfg.setStrategyId("test");
        stratCfg.setName("Test Strategy");
        stratCfg.setActive(true);
        stratCfg.setAllocationPercentage(100.0);
        stratCfg.setSizingType(SizingType.VOLATILITY_ATR);
        stratCfg.setRiskPercentage(2.0);
        stratCfg.setAtrMultiplier(2.0);
        gate.updateStrategyConfig(stratCfg);
        return gate;
    }

    @Test
    void equitySignalSizesInShares() {
        PreTradeGate gate = gateFor(testRiskConfig("1000000"));
        var signal = TradeSignal.builder()
                .symbol("NSE:SBIN-EQ")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(500)
                .suggestedStopLoss(490)
                .suggestedTarget(520)
                .strategyId("test")
                .vote(Timeframe.M5, Signal.BUY)
                .build();
        PreTradeResult result = gate.preTradeCheck(signal, Collections.emptyList(), 1_000_000);
        assertTrue(result.approved(), "Should be approved during market hours: " + result.rejectReason());
        assertEquals(1000, result.quantity());
    }

    @Test
    void futureSignalSizesInLots() {
        PreTradeGate gate = gateFor(testRiskConfig("5000000"));
        var signal = TradeSignal.builder()
                .symbol("NSE:NIFTY26MARFUT")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(22000)
                .suggestedStopLoss(21900)
                .suggestedTarget(22200)
                .strategyId("test")
                .vote(Timeframe.M5, Signal.BUY)
                .instrumentInfo(InstrumentInfo.derivative(SymbolType.EQUITY_FUTURE, 25, "FO"))
                .build();
        PreTradeResult result = gate.preTradeCheck(signal, Collections.emptyList(), 5_000_000);
        assertTrue(result.approved(), "Should be approved during market hours: " + result.rejectReason());
        assertTrue(result.quantity() > 0);
        assertEquals(0, result.quantity() % 25, "Quantity must be multiple of lot size 25");
    }

    @Test
    void futureMinimumOneLot() {
        PreTradeGate gate = gateFor(testRiskConfig("1000000"));
        var signal = TradeSignal.builder()
                .symbol("NSE:NIFTY26MARFUT")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(5000)
                .suggestedStopLoss(4900)
                .suggestedTarget(5200)
                .strategyId("test")
                .vote(Timeframe.M5, Signal.BUY)
                .instrumentInfo(InstrumentInfo.derivative(SymbolType.EQUITY_FUTURE, 75, "FO"))
                .build();
        PreTradeResult result = gate.preTradeCheck(signal, Collections.emptyList(), 1_000_000);
        assertTrue(result.approved());
        assertEquals(75, result.quantity());
        assertEquals(0, result.quantity() % 75);
    }

    @Test
    void futureSignalMultipleLots() {
        PreTradeGate gate = gateFor(testRiskConfig("1000000"));
        var signal = TradeSignal.builder()
                .symbol("NSE:BANKNIFTY26MARFUT")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(100)
                .suggestedStopLoss(90)
                .suggestedTarget(120)
                .strategyId("test")
                .vote(Timeframe.M5, Signal.BUY)
                .instrumentInfo(InstrumentInfo.derivative(SymbolType.EQUITY_FUTURE, 50, "FO"))
                .build();
        PreTradeResult result = gate.preTradeCheck(signal, Collections.emptyList(), 1_000_000);
        assertTrue(result.approved());
        assertEquals(1000, result.quantity());
        assertEquals(0, result.quantity() % 50);
    }

    @Test
    void optionSignalUsesMarginProductType() {
        var signal = TradeSignal.builder()
                .symbol("NSE:NIFTY26OCT22000CE")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(200)
                .suggestedStopLoss(180)
                .suggestedTarget(250)
                .strategyId("test")
                .instrumentInfo(InstrumentInfo.derivative(SymbolType.EQUITY_OPTION_MONTHLY, 25, "FO"))
                .build();
        assertEquals("MARGIN", signal.getProductType());
        assertEquals(25, signal.getLotSize());
    }

    private static RiskConfig testRiskConfig(String capital) {
        return RiskConfig.fromEnvironment(key -> switch (key) {
            case "RISK_INITIAL_CAPITAL_INR"       -> capital;
            case "RISK_MAX_DAILY_LOSS_INR"        -> "50000";
            case "RISK_MAX_DAILY_PROFIT_INR"      -> "100000";
            case "RISK_MAX_PER_TRADE_PCT"         -> "0.02";
            case "RISK_MAX_EXPOSURE_PER_SYMBOL_PCT" -> "0.50";
            case "RISK_MAX_QTY_PER_ORDER"         -> "1000";
            case "RISK_MAX_CONSECUTIVE_LOSSES"    -> "5";
            case "RISK_NO_NEW_TRADES_AFTER"       -> "15:00";
            case "RISK_MARKET_CLOSE_TIME"         -> "15:15";
            case "RISK_TRAILING_ACTIVATION_PCT"   -> "0.015";
            case "RISK_TRAILING_STEP_PCT"         -> "0.005";
            default -> null;
        });
    }
}
