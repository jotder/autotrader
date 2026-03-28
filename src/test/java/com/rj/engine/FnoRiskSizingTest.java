package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.*;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class FnoRiskSizingTest {

    private static final Supplier<ZonedDateTime> MARKET_HOURS_CLOCK = () -> ZonedDateTime.of(2026, 3, 28, 10, 30, 0, 0,
            ZoneId.of("Asia/Kolkata"));

    @Test
    void equitySignalSizesInShares() {
        RiskManager rm = new RiskManager(testRiskConfig(), MARKET_HOURS_CLOCK);
        // Capital=1000000, exposure=50%, entry=500, sl=490, risk/unit=10
        // riskBudget = 1000000*0.02 = 20000, rawQty = 20000/10 = 2000
        // exposureCap = 500000/500 = 1000, maxQty=1000 → min(2000,1000,1000)=1000
        var signal = TradeSignal.builder()
                .symbol("NSE:SBIN-EQ")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(500)
                .suggestedStopLoss(490)
                .suggestedTarget(520)
                .strategyId("test")
                .vote(Timeframe.M5, Signal.BUY)
                .build(); // no instrumentInfo → lotSize=1

        var result = rm.preTradeCheck(signal, Collections.emptyList(), 1000000);
        assertTrue(result.approved(), "Should be approved during market hours");
        assertEquals(1000, result.quantity());
    }

    @Test
    void futureSignalSizesInLots() {
        RiskManager rm = new RiskManager(testRiskConfig(), MARKET_HOURS_CLOCK);
        // Capital=5000000, exposure=50%, entry=22000, sl=21900, risk/unit=100
        // riskBudget = 5000000*0.02 = 100000, rawQty = 100000/100 = 1000
        // lotSize=25 → lotAligned = (1000/25)*25 = 1000
        // exposureCap = 2500000/22000 = 113, maxQty=1000 → min(1000,1000,113)=100 (lot-aligned: 100)
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

        var result = rm.preTradeCheck(signal, Collections.emptyList(), 5000000);
        assertTrue(result.approved(), "Should be approved during market hours");
        // Quantity should be lot-aligned (multiple of 25)
        assertTrue(result.quantity() > 0);
        assertEquals(0, result.quantity() % 25, "Quantity must be multiple of lot size 25");
    }

    @Test
    void futureMinimumOneLot() {
        RiskManager rm = new RiskManager(testRiskConfig(), MARKET_HOURS_CLOCK);
        // Capital=1000000, entry=5000, sl=4900, risk/unit=100
        // riskBudget = 1000000*0.02 = 20000, rawQty = 20000/100 = 200
        // lotSize=75 → lotAligned = (200/75)*75 = 150
        // exposureCap = 500000/5000 = 100 → re-aligned: (100/75)*75 = 75
        // maxQty=1000 → min(150,1000,75) = 75 (exactly 1 lot after re-alignment)
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

        var result = rm.preTradeCheck(signal, Collections.emptyList(), 1000000);
        assertTrue(result.approved(), "Should be approved during market hours");
        assertEquals(75, result.quantity()); // 1 lot after exposure cap re-alignment
        assertEquals(0, result.quantity() % 75, "Quantity must be multiple of lot size 75");
    }

    @Test
    void futureSignalMultipleLots() {
        RiskManager rm = new RiskManager(testRiskConfig(), MARKET_HOURS_CLOCK);
        // Capital=1000000, entry=100, sl=90, risk/unit=10
        // riskBudget = 1000000*0.02 = 20000, rawQty = 20000/10 = 2000
        // lotSize=50 → lotAligned = (2000/50)*50 = 2000
        // exposureCap = 500000/100 = 5000, maxQty=1000 → min(2000,1000,5000)=1000
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

        var result = rm.preTradeCheck(signal, Collections.emptyList(), 1000000);
        assertTrue(result.approved(), "Should be approved during market hours");
        assertEquals(1000, result.quantity());
        assertEquals(0, result.quantity() % 50, "Quantity must be multiple of lot size 50");
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

    private static RiskConfig testRiskConfig() {
        return RiskConfig.fromEnvironment(key -> switch (key) {
            case "RISK_INITIAL_CAPITAL_INR" -> "1000000";
            case "RISK_MAX_DAILY_LOSS_INR" -> "50000";
            case "RISK_MAX_DAILY_PROFIT_INR" -> "100000";
            case "RISK_MAX_PER_TRADE_PCT" -> "0.02";
            case "RISK_MAX_EXPOSURE_PER_SYMBOL_PCT" -> "0.50";
            case "RISK_MAX_QTY_PER_ORDER" -> "1000";
            case "RISK_MAX_CONSECUTIVE_LOSSES" -> "5";
            case "RISK_NO_NEW_TRADES_AFTER" -> "15:00";
            case "RISK_MARKET_CLOSE_TIME" -> "15:15";
            case "RISK_TRAILING_ACTIVATION_PCT" -> "0.015";
            case "RISK_TRAILING_STEP_PCT" -> "0.005";
            default -> null;
        });
    }
}
