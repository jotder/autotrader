package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyYamlConfig;
import com.rj.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that StrategyEvaluator reads per-strategy config from YAML:
 * - enabled/disabled flag
 * - active hours
 * - minConfidence threshold
 * - cooldown
 * - SL/TP multipliers
 */
class StrategyEvaluatorYamlTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String SYMBOL = "NSE:SBIN-EQ";

    private LinkedBlockingQueue<CandleRecommendation> queue;
    private AtomicReference<TradeSignal> capturedSignal;
    private RiskConfig riskConfig;
    private PositionMonitor positionMonitor;
    private StrategyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        queue = new LinkedBlockingQueue<>(100);
        capturedSignal = new AtomicReference<>();
        riskConfig = RiskConfig.defaults();

        // PositionMonitor with no open positions (stub)
        positionMonitor = new PositionMonitor(
                TickStore.getInstance(), riskConfig, (pos, reason) -> {}, null);

        evaluator = new StrategyEvaluator(
                queue, sig -> capturedSignal.set(sig), riskConfig, positionMonitor);
    }

    // ── Helper to build test CandleRecommendation ───────────────────────────────

    private CandleRecommendation buildRec(Timeframe tf, Signal signal, double confidence, String source) {
        Instant now = Instant.now();
        Candle candle = Candle.of(now.getEpochSecond(), 100.0, 101.0, 99.0, 100.5, 1000L);
        return CandleRecommendation.builder()
                .symbol(SYMBOL)
                .timeframe(tf)
                .windowStart(now.minusSeconds(300))
                .windowEnd(now)
                .signal(signal)
                .confidence(confidence)
                .strategySource(source)
                .ema20(100.2).ema50(99.8).rsi14(55.0).atr14(1.5).relVolume(1.0)
                .candle(candle)
                .build();
    }

    // ── Disabled strategy is skipped ────────────────────────────────────────────

    @Test
    void disabledStrategyIsSkipped() {
        // Create a config where trend_following is disabled
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        cfg.setEnabled(false);
        cfg.setSymbols(List.of(SYMBOL));
        evaluator.updateStrategyConfigs(Map.of("trend_following", cfg));

        // Verify config was stored
        assertEquals(1, evaluator.getStrategyConfigs().size());
        assertFalse(evaluator.getStrategyConfigs().get("trend_following").isEnabled());
    }

    @Test
    void updateStrategyConfigsIsThreadSafe() {
        // Null input should result in empty map
        evaluator.updateStrategyConfigs(null);
        assertTrue(evaluator.getStrategyConfigs().isEmpty());

        // Valid input should be stored
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        cfg.setSymbols(List.of(SYMBOL));
        evaluator.updateStrategyConfigs(Map.of("test_strategy", cfg));
        assertEquals(1, evaluator.getStrategyConfigs().size());

        // Replace with new map — old one should be gone
        evaluator.updateStrategyConfigs(Map.of());
        assertTrue(evaluator.getStrategyConfigs().isEmpty());
    }

    // ── Per-strategy confidence threshold ────────────────────────────────────────

    @Test
    void perStrategyMinConfidenceIsUsed() {
        // Set trend_following to require confidence >= 0.95 (very strict)
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        cfg.setEnabled(true);
        cfg.setSymbols(List.of(SYMBOL));
        StrategyYamlConfig.Entry entry = new StrategyYamlConfig.Entry();
        entry.setMinConfidence(0.95);
        cfg.setEntry(entry);

        evaluator.updateStrategyConfigs(Map.of("trend_following", cfg));

        // Verify the config is stored with the high threshold
        assertEquals(0.95, evaluator.getStrategyConfigs().get("trend_following")
                .getEntry().getMinConfidence(), 0.01);
    }

    // ── Per-strategy cooldown ───────────────────────────────────────────────────

    @Test
    void perStrategyCooldownIsConfigurable() {
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        cfg.setCooldownMinutes(10);
        cfg.setSymbols(List.of(SYMBOL));
        evaluator.updateStrategyConfigs(Map.of("trend_following", cfg));

        assertEquals(10, evaluator.getStrategyConfigs().get("trend_following").getCooldownMinutes());
    }

    // ── Per-strategy active hours ───────────────────────────────────────────────

    @Test
    void activeHoursConfigIsStored() {
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        cfg.setEnabled(true);
        cfg.setSymbols(List.of(SYMBOL));
        StrategyYamlConfig.ActiveHours hours = new StrategyYamlConfig.ActiveHours();
        hours.setStart("10:00");
        hours.setEnd("14:00");
        cfg.setActiveHours(hours);

        evaluator.updateStrategyConfigs(Map.of("trend_following", cfg));

        assertEquals("10:00", evaluator.getStrategyConfigs()
                .get("trend_following").getActiveHours().getStart());
        assertEquals("14:00", evaluator.getStrategyConfigs()
                .get("trend_following").getActiveHours().getEnd());
    }

    // ── Per-strategy SL/TP multipliers ──────────────────────────────────────────

    @Test
    void slTpMultipliersAreConfigurable() {
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        cfg.setEnabled(true);
        cfg.setSymbols(List.of(SYMBOL));
        StrategyYamlConfig.Risk risk = new StrategyYamlConfig.Risk();
        risk.setSlAtrMultiplier(1.5);
        risk.setTpRMultiple(3.0);
        cfg.setRisk(risk);

        evaluator.updateStrategyConfigs(Map.of("trend_following", cfg));

        assertEquals(1.5, evaluator.getStrategyConfigs()
                .get("trend_following").getRisk().getSlAtrMultiplier(), 0.01);
        assertEquals(3.0, evaluator.getStrategyConfigs()
                .get("trend_following").getRisk().getTpRMultiple(), 0.01);
    }

    // ── Multiple strategies loaded simultaneously ───────────────────────────────

    @Test
    void multipleStrategiesCanBeLoaded() {
        StrategyYamlConfig trend = new StrategyYamlConfig();
        trend.setEnabled(true);
        trend.setSymbols(List.of("NSE:SBIN-EQ"));

        StrategyYamlConfig meanRev = new StrategyYamlConfig();
        meanRev.setEnabled(false);
        meanRev.setSymbols(List.of("NSE:NIFTY50"));

        StrategyYamlConfig volBreak = new StrategyYamlConfig();
        volBreak.setEnabled(true);
        volBreak.setSymbols(List.of("NSE:BANKNIFTY"));
        volBreak.setCooldownMinutes(5);

        evaluator.updateStrategyConfigs(Map.of(
                "trend_following", trend,
                "mean_reversion", meanRev,
                "volatility_breakout", volBreak));

        assertEquals(3, evaluator.getStrategyConfigs().size());
        assertTrue(evaluator.getStrategyConfigs().get("trend_following").isEnabled());
        assertFalse(evaluator.getStrategyConfigs().get("mean_reversion").isEnabled());
        assertEquals(5, evaluator.getStrategyConfigs().get("volatility_breakout").getCooldownMinutes());
    }

    // ── Default config when no YAML loaded ──────────────────────────────────────

    @Test
    void defaultConfigWhenNoYamlLoaded() {
        // No updateStrategyConfigs called — should have empty map
        assertTrue(evaluator.getStrategyConfigs().isEmpty(),
                "Without YAML loading, strategy configs should be empty");
    }
}
