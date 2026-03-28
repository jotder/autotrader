package com.rj.engine;

import com.rj.config.StrategyYamlConfig;
import com.rj.model.Candle;
import com.rj.model.CandleRecommendation;
import com.rj.model.InstrumentInfo;
import com.rj.model.Signal;
import com.rj.model.Timeframe;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that CandleAnalyzer reads indicator periods and entry thresholds
 * from YAML config (via StrategyYamlConfig) instead of using hardcoded values.
 */
class CandleAnalyzerYamlTest {

    // ── Default constructor uses defaults ────────────────────────────────────────

    @Test
    void defaultConstructorUsesDefaultPeriods() {
        CandleAnalyzer analyzer = new CandleAnalyzer("NSE:SBIN-EQ", Timeframe.M5);

        assertEquals(20, analyzer.getEmaFastPeriod(), "Default EMA fast should be 20");
        assertEquals(50, analyzer.getEmaSlowPeriod(), "Default EMA slow should be 50");
        assertEquals(14, analyzer.getRsiPeriod(), "Default RSI period should be 14");
        assertEquals(14, analyzer.getAtrPeriod(), "Default ATR period should be 14");
        assertEquals(20, analyzer.getRelVolPeriod(), "Default relVol period should be 20");
        assertEquals(21, analyzer.getMinBarsForSignal(), "Default min bars should be 21");
        assertEquals(1.2, analyzer.getRelVolThreshold(), 0.01, "Default relVol threshold should be 1.2");
    }

    // ── Custom config constructor applies YAML values ───────────────────────────

    @Test
    void customConfigOverridesIndicatorPeriods() {
        StrategyYamlConfig.Indicators indicators = new StrategyYamlConfig.Indicators();
        indicators.setEmaFast(10);
        indicators.setEmaSlow(30);
        indicators.setRsiPeriod(7);
        indicators.setAtrPeriod(10);
        indicators.setRelVolPeriod(15);
        indicators.setMinCandles(30);

        StrategyYamlConfig.Entry entry = new StrategyYamlConfig.Entry();
        entry.setRelVolThreshold(3.5);

        CandleAnalyzer analyzer = new CandleAnalyzer("NSE:SBIN-EQ", Timeframe.M5, indicators, entry);

        assertEquals(10, analyzer.getEmaFastPeriod(), "EMA fast should be from config");
        assertEquals(30, analyzer.getEmaSlowPeriod(), "EMA slow should be from config");
        assertEquals(7, analyzer.getRsiPeriod(), "RSI period should be from config");
        assertEquals(10, analyzer.getAtrPeriod(), "ATR period should be from config");
        assertEquals(15, analyzer.getRelVolPeriod(), "relVol period should be from config");
        assertEquals(30, analyzer.getMinBarsForSignal(), "min bars should be from config");
        assertEquals(3.5, analyzer.getRelVolThreshold(), 0.01, "relVol threshold should be from config");
    }

    // ── Min bars gating respects config ─────────────────────────────────────────

    @Test
    void minBarsFromConfigControlsSignalGating() {
        // Set minCandles to 5 — analyzer will emit signal after 5 bars
        // BUT ta4j indicators (MACD 12,26) need more bars internally.
        // The minBarsForSignal gate fires BEFORE indicator access,
        // so with minCandles=5, bars 0-4 return INSUFFICIENT_DATA,
        // bar 5+ tries to compute indicators (may still HOLD if indicators
        // can't produce meaningful signals, but the source should NOT be INSUFFICIENT_DATA).
        StrategyYamlConfig.Indicators indicators = new StrategyYamlConfig.Indicators();
        indicators.setMinCandles(5);

        CandleAnalyzer analyzer = new CandleAnalyzer("NSE:SBIN-EQ", Timeframe.M5, indicators, new StrategyYamlConfig.Entry());

        // Verify the stored config value
        assertEquals(5, analyzer.getMinBarsForSignal());

        Instant baseTime = Instant.parse("2026-03-24T04:00:00Z");

        // Feed 4 candles — should still be INSUFFICIENT_DATA
        CandleRecommendation rec = null;
        for (int i = 0; i < 4; i++) {
            Candle candle = Candle.of(baseTime.plusSeconds(i * 300).getEpochSecond(),
                    100.0 + i, 101.0 + i, 99.0 + i, 100.5 + i, 1000L);
            Instant ws = baseTime.plusSeconds(i * 300);
            Instant we = ws.plusSeconds(300);
            rec = analyzer.addAndAnalyze(candle, ws, we, InstrumentInfo.EQUITY_DEFAULT);
        }
        assertNotNull(rec);
        assertEquals("INSUFFICIENT_DATA", rec.getStrategySource(),
                "With minCandles=5, should be insufficient at 4 bars");

        // With default constructor (minCandles=21), 4 bars is also insufficient
        CandleAnalyzer defaultAnalyzer = new CandleAnalyzer("NSE:SBIN-EQ", Timeframe.M5);
        assertEquals(21, defaultAnalyzer.getMinBarsForSignal(),
                "Default analyzer should have minBars=21");
    }

    @Test
    void defaultMinBarsRequiresMoreCandles() {
        // Default minCandles=21, feed 15 bars — should still be insufficient
        CandleAnalyzer analyzer = new CandleAnalyzer("NSE:SBIN-EQ", Timeframe.M5);

        Instant baseTime = Instant.parse("2026-03-24T04:00:00Z");

        CandleRecommendation rec = null;
        for (int i = 0; i < 15; i++) {
            Candle candle = Candle.of(baseTime.plusSeconds(i * 300).getEpochSecond(),
                    100.0 + i, 101.0 + i, 99.0 + i, 100.5 + i, 1000L);
            Instant ws = baseTime.plusSeconds(i * 300);
            Instant we = ws.plusSeconds(300);
            rec = analyzer.addAndAnalyze(candle, ws, we, InstrumentInfo.EQUITY_DEFAULT);
        }
        assertNotNull(rec);
        assertEquals("INSUFFICIENT_DATA", rec.getStrategySource(),
                "With default minCandles=21, should be insufficient at 15 bars");
    }

    // ── Two configs yield different analyzers ───────────────────────────────────

    @Test
    void differentConfigsProduceDifferentAnalyzers() {
        StrategyYamlConfig.Indicators fast = new StrategyYamlConfig.Indicators();
        fast.setEmaFast(5);
        fast.setEmaSlow(15);

        StrategyYamlConfig.Indicators slow = new StrategyYamlConfig.Indicators();
        slow.setEmaFast(50);
        slow.setEmaSlow(200);

        CandleAnalyzer fastAnalyzer = new CandleAnalyzer("SYM1", Timeframe.M5, fast, new StrategyYamlConfig.Entry());
        CandleAnalyzer slowAnalyzer = new CandleAnalyzer("SYM2", Timeframe.M5, slow, new StrategyYamlConfig.Entry());

        assertNotEquals(fastAnalyzer.getEmaFastPeriod(), slowAnalyzer.getEmaFastPeriod());
        assertNotEquals(fastAnalyzer.getEmaSlowPeriod(), slowAnalyzer.getEmaSlowPeriod());
    }
}
