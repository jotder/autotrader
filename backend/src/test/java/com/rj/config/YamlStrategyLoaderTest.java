package com.rj.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlStrategyLoaderTest {

    private YamlStrategyLoader loader;

    @BeforeEach
    void setUp() {
        loader = new YamlStrategyLoader();
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void load_validYaml_parsesAllStrategies() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        Map<String, StrategyYamlConfig> strategies = loader.load(yamlPath);

        assertEquals(2, strategies.size());
        assertTrue(strategies.containsKey("test_trend"));
        assertTrue(strategies.containsKey("test_disabled"));
    }

    @Test
    void load_validYaml_parsesEnabledFlag() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        Map<String, StrategyYamlConfig> strategies = loader.load(yamlPath);

        assertTrue(strategies.get("test_trend").isEnabled());
        assertFalse(strategies.get("test_disabled").isEnabled());
    }

    @Test
    void load_validYaml_parsesSymbolsAndTimeframe() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        StrategyYamlConfig cfg = loader.load(yamlPath).get("test_trend");

        assertEquals(2, cfg.getSymbols().size());
        assertTrue(cfg.getSymbols().contains("NSE:SBIN-EQ"));
        assertEquals("M5", cfg.getTimeframe());
    }

    @Test
    void load_validYaml_parsesActiveHours() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        StrategyYamlConfig cfg = loader.load(yamlPath).get("test_trend");

        assertEquals("09:15", cfg.getActiveHours().getStart());
        assertEquals("15:00", cfg.getActiveHours().getEnd());
    }

    @Test
    void load_validYaml_parsesCooldownAndMaxTrades() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        StrategyYamlConfig cfg = loader.load(yamlPath).get("test_trend");

        assertEquals(20, cfg.getCooldownMinutes());
        assertEquals(8, cfg.getMaxTradesPerDay());
    }

    @Test
    void load_validYaml_parsesIndicators() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        StrategyYamlConfig.Indicators ind = loader.load(yamlPath).get("test_trend").getIndicators();

        assertEquals(12, ind.getEmaFast());
        assertEquals(26, ind.getEmaSlow());
        assertEquals(14, ind.getRsiPeriod());
        assertEquals(14, ind.getAtrPeriod());
        assertEquals(20, ind.getRelVolPeriod());
        assertEquals(21, ind.getMinCandles());
    }

    @Test
    void load_validYaml_parsesEntrySection() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        StrategyYamlConfig.Entry entry = loader.load(yamlPath).get("test_trend").getEntry();

        assertEquals(0.80, entry.getMinConfidence(), 1e-9);
        assertEquals(1.5, entry.getRelVolThreshold(), 1e-9);
        assertEquals("STRONG_BULLISH", entry.getTrendStrength());
    }

    @Test
    void load_validYaml_parsesRiskSection() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        StrategyYamlConfig.Risk risk = loader.load(yamlPath).get("test_trend").getRisk();

        assertEquals(1.5, risk.getRiskPerTradePct(), 1e-9);
        assertEquals(1.8, risk.getSlAtrMultiplier(), 1e-9);
        assertEquals(2.5, risk.getTpRMultiple(), 1e-9);
        assertEquals(0.9, risk.getTrailingActivationPct(), 1e-9);
        assertEquals(0.5, risk.getTrailingStepPct(), 1e-9);
        assertEquals(15.0, risk.getMaxExposurePct(), 1e-9);
        assertEquals(500, risk.getMaxQty());
        assertEquals(2, risk.getMaxConsecutiveLosses());
    }

    @Test
    void load_validYaml_parsesOrderSection() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        StrategyYamlConfig.Order order = loader.load(yamlPath).get("test_trend").getOrder();

        assertEquals("MARKET", order.getType());
        assertEquals(0.03, order.getSlippageTolerance(), 1e-9);
        assertEquals("INTRADAY", order.getProductType());
    }

    @Test
    void load_validYaml_parsesLimitOrderType() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        StrategyYamlConfig.Order order = loader.load(yamlPath).get("test_disabled").getOrder();

        assertEquals("LIMIT", order.getType());
    }

    // ── Missing file ─────────────────────────────────────────────────────────

    @Test
    void load_missingFile_returnsEmptyMap(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("no-such-file.yaml");

        Map<String, StrategyYamlConfig> result = loader.load(nonExistent);

        assertNotNull(result);
        assertTrue(result.isEmpty(), "Expected empty map for missing file");
    }

    @Test
    void loadDefaults_missingFile_returnsDefaultConfig(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("missing-defaults.yaml");

        StrategyYamlConfig defaults = loader.loadDefaults(nonExistent);

        assertNotNull(defaults);
        // Built-in defaults should be applied
        assertEquals("M5", defaults.getTimeframe());
        assertEquals(25, defaults.getCooldownMinutes());
    }

    // ── Defaults YAML ────────────────────────────────────────────────────────

    @Test
    void loadDefaults_validFile_parsesGlobalDefaults() throws URISyntaxException {
        Path defaultsPath = resourcePath("test-defaults.yaml");

        StrategyYamlConfig defaults = loader.loadDefaults(defaultsPath);

        assertEquals("M5", defaults.getTimeframe());
        assertEquals(25, defaults.getCooldownMinutes());
        assertEquals(20, defaults.getIndicators().getEmaFast());
        assertEquals(50, defaults.getIndicators().getEmaSlow());
        assertEquals(14, defaults.getIndicators().getRsiPeriod());
        assertEquals(0.85, defaults.getEntry().getMinConfidence(), 1e-9);
        assertEquals("MARKET", defaults.getOrder().getType());
        assertEquals("INTRADAY", defaults.getOrder().getProductType());
    }

    // ── Malformed YAML ───────────────────────────────────────────────────────

    @Test
    void load_malformedYaml_throwsIllegalArgumentException(@TempDir Path tempDir) throws Exception {
        Path malformed = tempDir.resolve("malformed.yaml");
        Files.writeString(malformed, "strategies:\n  bad_indent:\n   wrong:\n  - also_bad: [unclosed");

        assertThrows(IllegalArgumentException.class, () -> loader.load(malformed));
    }

    @Test
    void load_wrongRootType_throwsIllegalArgumentException(@TempDir Path tempDir) throws Exception {
        Path wrongType = tempDir.resolve("wrong-root.yaml");
        Files.writeString(wrongType, "- item1\n- item2\n");

        assertThrows(IllegalArgumentException.class, () -> loader.load(wrongType));
    }

    @Test
    void load_emptyFile_returnsEmptyMap(@TempDir Path tempDir) throws Exception {
        Path empty = tempDir.resolve("empty.yaml");
        Files.writeString(empty, "");

        Map<String, StrategyYamlConfig> result = loader.load(empty);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void load_noStrategiesKey_returnsEmptyMap(@TempDir Path tempDir) throws Exception {
        Path noStrategies = tempDir.resolve("no-strategies.yaml");
        Files.writeString(noStrategies, "other_key:\n  foo: bar\n");

        Map<String, StrategyYamlConfig> result = loader.load(noStrategies);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ── Default value fallback ────────────────────────────────────────────────

    @Test
    void load_strategyMissingOptionalFields_usesDefaults(@TempDir Path tempDir) throws Exception {
        Path minimal = tempDir.resolve("minimal.yaml");
        Files.writeString(minimal,
                "strategies:\n" +
                "  minimal_strat:\n" +
                "    enabled: true\n" +
                "    symbols: [\"NSE:TEST-EQ\"]\n" +
                "    timeframe: M15\n");

        StrategyYamlConfig cfg = loader.load(minimal).get("minimal_strat");

        assertNotNull(cfg);
        assertTrue(cfg.isEnabled());
        assertEquals("M15", cfg.getTimeframe());
        // Sections not in YAML should have default values
        assertEquals(20, cfg.getIndicators().getEmaFast());
        assertEquals(0.85, cfg.getEntry().getMinConfidence(), 1e-9);
        assertEquals("MARKET", cfg.getOrder().getType());
    }

    // ── Production intraday.yaml ──────────────────────────────────────────────

    @Test
    void load_productionIntradayYaml_parsesThreeStrategies() {
        Path productionYaml = Path.of("config/strategies/intraday.yaml");
        // Skip if running outside project root
        org.junit.jupiter.api.Assumptions.assumeTrue(
                productionYaml.toFile().exists(),
                "Skipping: config/strategies/intraday.yaml not found relative to working directory");

        Map<String, StrategyYamlConfig> strategies = loader.load(productionYaml);

        assertEquals(3, strategies.size());
        assertTrue(strategies.containsKey("trend_following"));
        assertTrue(strategies.containsKey("mean_reversion"));
        assertTrue(strategies.containsKey("volatility_breakout"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Path resourcePath(String resourceName) throws URISyntaxException {
        URL url = getClass().getClassLoader().getResource(resourceName);
        assertNotNull(url, "Test resource not found: " + resourceName);
        return Path.of(url.toURI());
    }
}
