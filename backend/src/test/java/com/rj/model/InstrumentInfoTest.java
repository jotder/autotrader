package com.rj.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentInfoTest {

    @Test
    void equityDefault() {
        var info = InstrumentInfo.EQUITY_DEFAULT;
        assertEquals(SymbolType.EQUITY, info.symbolType());
        assertEquals("INTRADAY", info.productType());
        assertEquals(1, info.lotSize());
        assertEquals("CM", info.segment());
        assertFalse(info.isDerivative());
        assertFalse(info.isOption());
        assertFalse(info.isFuture());
    }

    @Test
    void derivativeFuture() {
        var info = InstrumentInfo.derivative(SymbolType.EQUITY_FUTURE, 25, "FO");
        assertEquals(SymbolType.EQUITY_FUTURE, info.symbolType());
        assertEquals("MARGIN", info.productType());
        assertEquals(25, info.lotSize());
        assertEquals("FO", info.segment());
        assertTrue(info.isDerivative());
        assertTrue(info.isFuture());
        assertFalse(info.isOption());
    }

    @Test
    void derivativeOption() {
        var info = InstrumentInfo.derivative(SymbolType.EQUITY_OPTION_MONTHLY, 25, "FO");
        assertTrue(info.isDerivative());
        assertTrue(info.isOption());
        assertFalse(info.isFuture());
        assertEquals("MARGIN", info.productType());
    }

    @Test
    void fromTypeEquity() {
        var info = InstrumentInfo.fromType(SymbolType.EQUITY, 1);
        assertEquals(InstrumentInfo.EQUITY_DEFAULT, info);
    }

    @Test
    void fromTypeNull() {
        var info = InstrumentInfo.fromType(null, 1);
        assertEquals(InstrumentInfo.EQUITY_DEFAULT, info);
    }

    @Test
    void fromTypeFuture() {
        var info = InstrumentInfo.fromType(SymbolType.COMMODITY_FUTURE, 100);
        assertEquals("MARGIN", info.productType());
        assertEquals(100, info.lotSize());
        assertEquals("COM", info.segment());
    }

    @Test
    void fromTypeCurrencyOption() {
        var info = InstrumentInfo.fromType(SymbolType.CURRENCY_OPTION_WEEKLY, 1000);
        assertEquals("MARGIN", info.productType());
        assertEquals(1000, info.lotSize());
        assertEquals("CD", info.segment());
    }

    @Test
    void fromTypeMinLotSize() {
        // Lot size 0 should become 1
        var info = InstrumentInfo.fromType(SymbolType.EQUITY_FUTURE, 0);
        assertEquals(1, info.lotSize());
    }

    @Test
    void tradeSignalProductType() {
        var signal = TradeSignal.builder()
                .symbol("NSE:NIFTY26MARFUT")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(22000)
                .suggestedStopLoss(21900)
                .suggestedTarget(22200)
                .strategyId("test")
                .instrumentInfo(InstrumentInfo.derivative(SymbolType.EQUITY_FUTURE, 25, "FO"))
                .build();

        assertEquals("MARGIN", signal.getProductType());
        assertEquals(25, signal.getLotSize());
    }

    @Test
    void tradeSignalDefaultProductType() {
        var signal = TradeSignal.builder()
                .symbol("NSE:SBIN-EQ")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(500)
                .suggestedStopLoss(490)
                .suggestedTarget(520)
                .strategyId("test")
                .build(); // no instrumentInfo

        assertEquals("INTRADAY", signal.getProductType());
        assertEquals(1, signal.getLotSize());
    }

    @Test
    void openPositionProductType() {
        var pos = new OpenPosition("NSE:NIFTY26MARFUT", "corr1", "strat1",
                Signal.BUY, 22000, 25, 21900, 22200,
                java.time.Instant.now(),
                InstrumentInfo.derivative(SymbolType.EQUITY_FUTURE, 25, "FO"));

        assertEquals("MARGIN", pos.getProductType());
    }

    @Test
    void openPositionDefaultProductType() {
        var pos = new OpenPosition("NSE:SBIN-EQ", "corr1", "strat1",
                Signal.BUY, 500, 10, 490, 520,
                java.time.Instant.now()); // no instrumentInfo

        assertEquals("INTRADAY", pos.getProductType());
    }
}
