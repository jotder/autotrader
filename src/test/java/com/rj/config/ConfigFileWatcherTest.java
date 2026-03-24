package com.rj.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConfigFileWatcher} — YAML hot-reload via WatchService.
 */
class ConfigFileWatcherTest {

    @TempDir
    Path tempDir;

    private Path strategiesDir;
    private Path strategiesFile;
    private Path defaultsFile;
    private YamlStrategyLoader loader;
    private ConfigValidator validator;
    private ConfigFileWatcher watcher;

    private static final String VALID_YAML = """
            strategies:
              trend_following:
                enabled: true
                symbols: ["NSE:SBIN-EQ"]
                timeframe: M5
                cooldown_minutes: 25
                max_trades_per_day: 10
                active_hours:
                  start: "09:15"
                  end: "15:00"
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
                  trailing_step_pct: 1.0
                  max_exposure_pct: 20.0
                  max_qty: 1000
                  max_consecutive_losses: 3
                order:
                  type: MARKET
                  slippage_tolerance: 0.05
                  product_type: INTRADAY
            """;

    private static final String VALID_YAML_UPDATED = """
            strategies:
              trend_following:
                enabled: true
                symbols: ["NSE:SBIN-EQ", "NSE:RELIANCE-EQ"]
                timeframe: M5
                cooldown_minutes: 30
                max_trades_per_day: 15
                active_hours:
                  start: "09:15"
                  end: "15:00"
                indicators:
                  ema_fast: 10
                  ema_slow: 30
                  rsi_period: 14
                  atr_period: 14
                  rel_vol_period: 20
                  min_candles: 21
                entry:
                  min_confidence: 0.90
                  rel_vol_threshold: 1.5
                  trend_strength: STRONG_BULLISH
                risk:
                  risk_per_trade_pct: 1.5
                  sl_atr_multiplier: 1.5
                  tp_r_multiple: 3.0
                  trailing_activation_pct: 1.0
                  trailing_step_pct: 1.0
                  max_exposure_pct: 15.0
                  max_qty: 500
                  max_consecutive_losses: 2
                order:
                  type: MARKET
                  slippage_tolerance: 0.05
                  product_type: INTRADAY
            """;

    private static final String INVALID_YAML = """
            strategies:
              bad_strategy:
                enabled: true
                symbols: []
                timeframe: INVALID
                indicators:
                  ema_fast: 50
                  ema_slow: 20
                risk:
                  risk_per_trade_pct: 999.0
            """;

    private static final String DEFAULTS_YAML = """
            defaults:
              timeframe: M5
              cooldown_minutes: 25
              max_trades_per_day: 10
              active_hours:
                start: "09:15"
                end: "15:00"
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
                trailing_step_pct: 1.0
                max_exposure_pct: 20.0
                max_qty: 1000
                max_consecutive_losses: 3
              order:
                type: MARKET
                slippage_tolerance: 0.05
                product_type: INTRADAY
            """;

    @BeforeEach
    void setUp() throws IOException {
        strategiesDir = tempDir.resolve("strategies");
        Files.createDirectories(strategiesDir);
        strategiesFile = strategiesDir.resolve("intraday.yaml");
        defaultsFile = tempDir.resolve("defaults.yaml");

        Files.writeString(strategiesFile, VALID_YAML);
        Files.writeString(defaultsFile, DEFAULTS_YAML);

        loader = new YamlStrategyLoader();
        validator = new ConfigValidator();
    }

    @AfterEach
    void tearDown() {
        if (watcher != null) {
            watcher.stop();
        }
    }

    @Test
    void startAndStop() throws Exception {
        var ref = new AtomicReference<Map<String, StrategyYamlConfig>>();
        watcher = new ConfigFileWatcher(
                strategiesDir, strategiesFile, defaultsFile,
                loader, validator, ref::set, 50L);

        watcher.start();
        assertTrue(watcher.isRunning());

        watcher.stop();
        // Give the virtual thread time to exit
        Thread.sleep(200);
        assertFalse(watcher.isRunning());
    }

    @Test
    void startIsIdempotent() throws Exception {
        var ref = new AtomicReference<Map<String, StrategyYamlConfig>>();
        watcher = new ConfigFileWatcher(
                strategiesDir, strategiesFile, defaultsFile,
                loader, validator, ref::set, 50L);

        watcher.start();
        watcher.start(); // second call should be no-op
        assertTrue(watcher.isRunning());
    }

    @Test
    void startThrowsIfDirectoryMissing() {
        Path nonExistent = tempDir.resolve("no-such-dir");
        var ref = new AtomicReference<Map<String, StrategyYamlConfig>>();
        watcher = new ConfigFileWatcher(
                nonExistent, strategiesFile, defaultsFile,
                loader, validator, ref::set, 50L);

        assertThrows(IOException.class, () -> watcher.start());
        assertFalse(watcher.isRunning());
    }

    @Test
    void detectsValidYamlChange() throws Exception {
        var latch = new CountDownLatch(1);
        var reloaded = new AtomicReference<Map<String, StrategyYamlConfig>>();

        watcher = new ConfigFileWatcher(
                strategiesDir, strategiesFile, defaultsFile,
                loader, validator,
                config -> { reloaded.set(config); latch.countDown(); },
                50L);

        // Initial load to seed lastValidConfig
        loader.reloadWithRollback(strategiesFile, defaultsFile, validator);

        watcher.start();
        // Give WatchService time to register
        Thread.sleep(300);

        // Modify the file
        Files.writeString(strategiesFile, VALID_YAML_UPDATED);

        boolean notified = latch.await(10, TimeUnit.SECONDS);
        assertTrue(notified, "Reload callback was not invoked within timeout");

        Map<String, StrategyYamlConfig> result = reloaded.get();
        assertNotNull(result);
        assertTrue(result.containsKey("trend_following"));

        StrategyYamlConfig cfg = result.get("trend_following");
        assertEquals(2, cfg.getSymbols().size(), "Updated YAML should have 2 symbols");
        assertEquals(30, cfg.getCooldownMinutes(), "Cooldown should be updated to 30");
        assertEquals(0.90, cfg.getEntry().getMinConfidence(), 0.01,
                "Min confidence should be updated to 0.90");
    }

    @Test
    void rejectsInvalidYamlChange() throws Exception {
        var callCount = new AtomicInteger(0);
        var lastConfig = new AtomicReference<Map<String, StrategyYamlConfig>>();

        watcher = new ConfigFileWatcher(
                strategiesDir, strategiesFile, defaultsFile,
                loader, validator,
                config -> { callCount.incrementAndGet(); lastConfig.set(config); },
                50L);

        // Initial load to seed lastValidConfig
        Map<String, StrategyYamlConfig> initial = loader.reloadWithRollback(
                strategiesFile, defaultsFile, validator);
        assertFalse(initial.isEmpty());

        watcher.start();
        Thread.sleep(300);

        // Write invalid YAML
        Files.writeString(strategiesFile, INVALID_YAML);

        // Wait for the watcher to process
        Thread.sleep(2000);

        // The callback should still be invoked (with the rollback/previous config)
        // and the loader's lastValidConfig should still be the original valid config
        Map<String, StrategyYamlConfig> current = loader.getLastValidConfig();
        assertNotNull(current);
        assertTrue(current.containsKey("trend_following"),
                "Previous valid config should be retained after invalid change");

        // Verify the original config values are preserved
        StrategyYamlConfig cfg = current.get("trend_following");
        assertEquals("M5", cfg.getTimeframe());
        assertEquals(1, cfg.getSymbols().size());
    }

    @Test
    void ignoresNonYamlFiles() throws Exception {
        var callCount = new AtomicInteger(0);

        watcher = new ConfigFileWatcher(
                strategiesDir, strategiesFile, defaultsFile,
                loader, validator,
                config -> callCount.incrementAndGet(),
                50L);

        // Seed initial config
        loader.reloadWithRollback(strategiesFile, defaultsFile, validator);

        watcher.start();
        Thread.sleep(300);

        // Create a non-YAML file — should not trigger reload
        Files.writeString(strategiesDir.resolve("notes.txt"), "not yaml");

        Thread.sleep(1500);
        assertEquals(0, callCount.get(),
                "Non-YAML file change should not trigger reload");
    }

    @Test
    void reloadCompletesWithinTimeout() throws Exception {
        var latch = new CountDownLatch(1);
        var elapsedRef = new AtomicReference<Long>();

        watcher = new ConfigFileWatcher(
                strategiesDir, strategiesFile, defaultsFile,
                loader, validator,
                config -> {
                    elapsedRef.set(System.currentTimeMillis());
                    latch.countDown();
                },
                50L);

        loader.reloadWithRollback(strategiesFile, defaultsFile, validator);

        watcher.start();
        Thread.sleep(300);

        long beforeWrite = System.currentTimeMillis();
        Files.writeString(strategiesFile, VALID_YAML_UPDATED);

        boolean notified = latch.await(10, TimeUnit.SECONDS);
        assertTrue(notified, "Reload should complete within timeout");

        long elapsed = elapsedRef.get() - beforeWrite;
        // Reload should be fast — well under 500ms for validation+parse
        // (WatchService delivery itself can vary by OS, so we use a generous bound)
        assertTrue(elapsed < 5000,
                "Reload took too long: " + elapsed + "ms (expected < 5000ms including OS delay)");
    }
}
