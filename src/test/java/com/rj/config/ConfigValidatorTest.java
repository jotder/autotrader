package com.rj.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConfigValidator} and {@link YamlStrategyLoader#reloadWithRollback}.
 */
class ConfigValidatorTest {

    private ConfigValidator validator;
    private YamlStrategyLoader loader;

    @BeforeEach
    void setUp() {
        validator = new ConfigValidator();
        loader = new YamlStrategyLoader();
    }

    // ── Helper: build a minimal valid config ─────────────────────────────────

    private StrategyYamlConfig validConfig() {
        StrategyYamlConfig cfg = new StrategyYamlConfig();
        cfg.setSymbols(List.of("NSE:NIFTY50-INDEX"));
        cfg.setTimeframe("M5");
        cfg.setCooldownMinutes(20);
        cfg.setMaxTradesPerDay(5);

        StrategyYamlConfig.Indicators ind = new StrategyYamlConfig.Indicators();
        ind.setEmaFast(20);
        ind.setEmaSlow(50);
        ind.setRsiPeriod(14);
        ind.setAtrPeriod(14);
        ind.setRelVolPeriod(20);
        ind.setMinCandles(21);
        cfg.setIndicators(ind);

        StrategyYamlConfig.Entry entry = new StrategyYamlConfig.Entry();
        entry.setMinConfidence(0.85);
        entry.setRelVolThreshold(1.2);
        entry.setTrendStrength("STRONG_BULLISH");
        cfg.setEntry(entry);

        StrategyYamlConfig.Risk risk = new StrategyYamlConfig.Risk();
        risk.setRiskPerTradePct(2.0);
        risk.setSlAtrMultiplier(2.0);
        risk.setTpRMultiple(2.0);
        risk.setTrailingActivationPct(1.0);
        risk.setTrailingStepPct(0.5);
        risk.setMaxExposurePct(20.0);
        risk.setMaxQty(500);
        risk.setMaxConsecutiveLosses(3);
        cfg.setRisk(risk);

        StrategyYamlConfig.Order order = new StrategyYamlConfig.Order();
        order.setType("MARKET");
        order.setSlippageTolerance(0.05);
        order.setProductType("INTRADAY");
        cfg.setOrder(order);

        return cfg;
    }

    // ── Passing tests ─────────────────────────────────────────────────────────

    @Test
    void validConfig_passes() {
        ConfigValidator.ValidationResult result = validator.validate("trend_following", validConfig());
        assertTrue(result.valid(), "Valid config should pass; errors: " + result.errors());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validConfigFromYaml_passes() {
        Path yamlPath = Path.of("src/test/resources/test-strategies-valid.yaml");
        org.junit.jupiter.api.Assumptions.assumeTrue(yamlPath.toFile().exists(),
                "Skipping — test YAML not present at: " + yamlPath);
        Map<String, StrategyYamlConfig> configs = loader.load(yamlPath);
        ConfigValidator.ValidationResult result = validator.validateAll(configs);
        assertTrue(result.valid(), "Valid YAML should pass; errors: " + result.errors());
    }

    @Test
    void allValidTimeframes_pass() {
        for (String tf : List.of("M1", "M5", "M15", "H1", "D")) {
            StrategyYamlConfig cfg = validConfig();
            cfg.setTimeframe(tf);
            ConfigValidator.ValidationResult result = validator.validate("s", cfg);
            assertTrue(result.valid(), "Timeframe " + tf + " should be valid");
        }
    }

    // ── Missing required field ────────────────────────────────────────────────

    @Test
    void missingSymbols_fails() {
        StrategyYamlConfig cfg = validConfig();
        cfg.setSymbols(List.of());
        ConfigValidator.ValidationResult result = validator.validate("trend_following", cfg);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("symbols")),
                "Error should mention 'symbols'");
    }

    // ── Out-of-range numeric values ───────────────────────────────────────────

    @Test
    void riskPctTooHigh_fails() {
        StrategyYamlConfig cfg = validConfig();
        cfg.getRisk().setRiskPerTradePct(15.0);
        ConfigValidator.ValidationResult result = validator.validate("trend_following", cfg);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("risk_per_trade_pct")),
                "Error should mention 'risk_per_trade_pct'");
    }

    @Test
    void riskPctTooLow_fails() {
        StrategyYamlConfig cfg = validConfig();
        cfg.getRisk().setRiskPerTradePct(0.0);
        ConfigValidator.ValidationResult result = validator.validate("s", cfg);
        assertFalse(result.valid());
    }

    @Test
    void confidenceOutOfRange_fails() {
        StrategyYamlConfig cfg = validConfig();
        cfg.getEntry().setMinConfidence(1.5);
        ConfigValidator.ValidationResult result = validator.validate("s", cfg);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("min_confidence")));
    }

    @Test
    void emaFastGreaterThanEmaSlow_fails() {
        StrategyYamlConfig cfg = validConfig();
        cfg.getIndicators().setEmaFast(60);
        cfg.getIndicators().setEmaSlow(50);
        ConfigValidator.ValidationResult result = validator.validate("s", cfg);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("ema_fast")));
    }

    // ── Invalid enum values ───────────────────────────────────────────────────

    @Test
    void invalidTimeframe_fails() {
        StrategyYamlConfig cfg = validConfig();
        cfg.setTimeframe("W1");
        ConfigValidator.ValidationResult result = validator.validate("trend_following", cfg);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("timeframe")),
                "Error should mention 'timeframe'");
    }

    @Test
    void invalidOrderType_fails() {
        StrategyYamlConfig cfg = validConfig();
        cfg.getOrder().setType("STOP");
        ConfigValidator.ValidationResult result = validator.validate("trend_following", cfg);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("order.type")),
                "Error should mention 'order.type'");
    }

    @Test
    void invalidProductType_fails() {
        StrategyYamlConfig cfg = validConfig();
        cfg.getOrder().setProductType("OPTIONS");
        ConfigValidator.ValidationResult result = validator.validate("s", cfg);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("product_type")));
    }

    @Test
    void invalidTrendStrength_fails() {
        StrategyYamlConfig cfg = validConfig();
        cfg.getEntry().setTrendStrength("MODERATE");
        ConfigValidator.ValidationResult result = validator.validate("trend_following", cfg);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("trend_strength")),
                "Error should mention 'trend_strength'");
    }

    // ── validateAll ───────────────────────────────────────────────────────────

    @Test
    void validateAll_aggregatesErrors() {
        StrategyYamlConfig good = validConfig();
        StrategyYamlConfig bad = validConfig();
        bad.getOrder().setType("BAD_TYPE");
        bad.setTimeframe("X99");

        ConfigValidator.ValidationResult result = validator.validateAll(
                Map.of("good_strategy", good, "bad_strategy", bad));

        assertFalse(result.valid());
        // bad_strategy should contribute at least 2 errors
        long badCount = result.errors().stream()
                .filter(e -> e.contains("bad_strategy")).count();
        assertTrue(badCount >= 2, "Expected >= 2 errors for bad_strategy, got: " + result.errors());
    }

    // ── Rollback via YamlStrategyLoader ──────────────────────────────────────

    @Test
    void rollback_retainsPreviousConfigOnInvalidReload(@TempDir Path dir) throws IOException {
        // Write a valid config first
        Path validFile = dir.resolve("valid.yaml");
        Files.writeString(validFile, """
                strategies:
                  trend_following:
                    enabled: true
                    symbols: ["NSE:NIFTY50-INDEX"]
                    timeframe: M5
                    cooldown_minutes: 20
                    max_trades_per_day: 5
                    indicators:
                      ema_fast: 20
                      ema_slow: 50
                      rsi_period: 14
                      atr_period: 14
                      rel_vol_period: 20
                      min_candles: 21
                    entry:
                      min_confidence: 0.85
                      rel_vol_threshold: 1.2
                      trend_strength: STRONG_BULLISH
                    risk:
                      risk_per_trade_pct: 2.0
                      sl_atr_multiplier: 2.0
                      tp_r_multiple: 2.0
                      trailing_activation_pct: 1.0
                      trailing_step_pct: 0.5
                      max_exposure_pct: 20.0
                      max_qty: 500
                      max_consecutive_losses: 3
                    order:
                      type: MARKET
                      slippage_tolerance: 0.05
                      product_type: INTRADAY
                """);

        Path noDefaults = dir.resolve("defaults.yaml"); // does not exist — that's OK

        // First load: should succeed and populate lastValidConfig
        Map<String, StrategyYamlConfig> first = loader.reloadWithRollback(validFile, noDefaults, validator);
        assertEquals(1, first.size());
        assertTrue(first.containsKey("trend_following"));

        // Write an invalid config (bad order type)
        Path invalidFile = dir.resolve("invalid.yaml");
        Files.writeString(invalidFile, """
                strategies:
                  trend_following:
                    enabled: true
                    symbols: ["NSE:NIFTY50-INDEX"]
                    timeframe: M5
                    cooldown_minutes: 20
                    max_trades_per_day: 5
                    indicators:
                      ema_fast: 20
                      ema_slow: 50
                      rsi_period: 14
                      atr_period: 14
                      rel_vol_period: 20
                      min_candles: 21
                    entry:
                      min_confidence: 0.85
                      rel_vol_threshold: 1.2
                      trend_strength: STRONG_BULLISH
                    risk:
                      risk_per_trade_pct: 99.0
                      sl_atr_multiplier: 2.0
                      tp_r_multiple: 2.0
                      trailing_activation_pct: 1.0
                      trailing_step_pct: 0.5
                      max_exposure_pct: 20.0
                      max_qty: 500
                      max_consecutive_losses: 3
                    order:
                      type: BAD_ORDER_TYPE
                      slippage_tolerance: 0.05
                      product_type: INTRADAY
                """);

        // Second load: invalid → should rollback to previous
        Map<String, StrategyYamlConfig> second = loader.reloadWithRollback(invalidFile, noDefaults, validator);

        // Should still be the first valid config
        assertEquals(1, second.size());
        assertTrue(second.containsKey("trend_following"));
        // Confirm the old (valid) value is retained, not the bad 99.0
        assertEquals(2.0, second.get("trend_following").getRisk().getRiskPerTradePct(), 0.001,
                "Rollback should retain previous risk_per_trade_pct of 2.0");
    }

    @Test
    void rollback_updatesOnValidReload(@TempDir Path dir) throws IOException {
        // First: load with risk = 2.0
        Path firstFile = dir.resolve("first.yaml");
        Files.writeString(firstFile, """
                strategies:
                  s1:
                    enabled: true
                    symbols: ["NSE:NIFTY50-INDEX"]
                    timeframe: M5
                    cooldown_minutes: 20
                    max_trades_per_day: 5
                    indicators:
                      ema_fast: 10
                      ema_slow: 50
                      rsi_period: 14
                      atr_period: 14
                      rel_vol_period: 20
                      min_candles: 21
                    entry:
                      min_confidence: 0.80
                      rel_vol_threshold: 1.1
                      trend_strength: BULLISH
                    risk:
                      risk_per_trade_pct: 2.0
                      sl_atr_multiplier: 2.0
                      tp_r_multiple: 2.0
                      trailing_activation_pct: 1.0
                      trailing_step_pct: 0.5
                      max_exposure_pct: 15.0
                      max_qty: 200
                      max_consecutive_losses: 3
                    order:
                      type: LIMIT
                      slippage_tolerance: 0.03
                      product_type: CNC
                """);

        Path noDefaults = dir.resolve("defaults.yaml");
        loader.reloadWithRollback(firstFile, noDefaults, validator);

        // Second: valid reload with risk = 3.0
        Path secondFile = dir.resolve("second.yaml");
        Files.writeString(secondFile, """
                strategies:
                  s1:
                    enabled: true
                    symbols: ["NSE:NIFTY50-INDEX"]
                    timeframe: H1
                    cooldown_minutes: 30
                    max_trades_per_day: 3
                    indicators:
                      ema_fast: 10
                      ema_slow: 50
                      rsi_period: 14
                      atr_period: 14
                      rel_vol_period: 20
                      min_candles: 21
                    entry:
                      min_confidence: 0.90
                      rel_vol_threshold: 1.5
                      trend_strength: NEUTRAL
                    risk:
                      risk_per_trade_pct: 3.0
                      sl_atr_multiplier: 1.5
                      tp_r_multiple: 3.0
                      trailing_activation_pct: 2.0
                      trailing_step_pct: 1.0
                      max_exposure_pct: 25.0
                      max_qty: 300
                      max_consecutive_losses: 2
                    order:
                      type: MARKET
                      slippage_tolerance: 0.05
                      product_type: INTRADAY
                """);

        Map<String, StrategyYamlConfig> result = loader.reloadWithRollback(secondFile, noDefaults, validator);
        assertEquals(3.0, result.get("s1").getRisk().getRiskPerTradePct(), 0.001,
                "Valid reload should update to new risk_per_trade_pct 3.0");
        assertEquals("H1", result.get("s1").getTimeframe());
    }

    @Test
    void rollback_malformedYaml_retainsPrevious(@TempDir Path dir) throws IOException {
        // Load valid first
        Path validFile = dir.resolve("valid.yaml");
        Files.writeString(validFile, """
                strategies:
                  s1:
                    enabled: true
                    symbols: ["NSE:RELIANCE-EQ"]
                    timeframe: M15
                    cooldown_minutes: 15
                    max_trades_per_day: 4
                    indicators:
                      ema_fast: 10
                      ema_slow: 50
                      rsi_period: 14
                      atr_period: 14
                      rel_vol_period: 20
                      min_candles: 21
                    entry:
                      min_confidence: 0.80
                      rel_vol_threshold: 1.0
                      trend_strength: BULLISH
                    risk:
                      risk_per_trade_pct: 1.5
                      sl_atr_multiplier: 2.0
                      tp_r_multiple: 2.0
                      trailing_activation_pct: 1.0
                      trailing_step_pct: 0.5
                      max_exposure_pct: 10.0
                      max_qty: 100
                      max_consecutive_losses: 3
                    order:
                      type: MARKET
                      slippage_tolerance: 0.05
                      product_type: INTRADAY
                """);

        Path noDefaults = dir.resolve("defaults.yaml");
        loader.reloadWithRollback(validFile, noDefaults, validator);

        // Now try malformed YAML
        Path malformedFile = dir.resolve("malformed.yaml");
        Files.writeString(malformedFile, "strategies: [\n  - bad: {unclosed");

        Map<String, StrategyYamlConfig> result = loader.reloadWithRollback(malformedFile, noDefaults, validator);
        assertEquals(1, result.size(), "Malformed YAML should retain previous config");
        assertTrue(result.containsKey("s1"));
    }

    @Test
    void getLastValidConfig_returnsEmptyBeforeAnyLoad() {
        YamlStrategyLoader fresh = new YamlStrategyLoader();
        assertTrue(fresh.getLastValidConfig().isEmpty(),
                "No config loaded yet — should return empty map");
    }
}
