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

/**
 * Unit tests for AT-002: StrategyRiskConfig, StrategyOrderConfig, and
 * YamlStrategyLoader.loadWithDefaults() default-merge behaviour.
 */
class PerStrategyConfigTest {

    private YamlStrategyLoader loader;

    @BeforeEach
    void setUp() {
        loader = new YamlStrategyLoader();
    }

    // ── StrategyRiskConfig: factory from parsed YAML ──────────────────────────

    @Test
    void strategyRiskConfig_fromYamlRisk_allFieldsMapped() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        StrategyYamlConfig cfg = loader.load(yamlPath).get("test_trend");
        StrategyRiskConfig risk = StrategyRiskConfig.from(cfg.getRisk());

        assertEquals(1.5, risk.riskPerTradePct(), 1e-9);
        assertEquals(1.8, risk.slAtrMultiplier(), 1e-9);
        assertEquals(2.5, risk.tpRMultiple(), 1e-9);
        assertEquals(0.9, risk.trailingActivationPct(), 1e-9);
        assertEquals(0.5, risk.trailingStepPct(), 1e-9);
        assertEquals(15.0, risk.maxExposurePct(), 1e-9);
        assertEquals(500, risk.maxQty());
        assertEquals(2, risk.maxConsecutiveLosses());
    }

    @Test
    void strategyRiskConfig_defaults_matchExpectedValues() {
        StrategyRiskConfig defaults = StrategyRiskConfig.defaults();

        assertEquals(2.0, defaults.riskPerTradePct(), 1e-9);
        assertEquals(2.0, defaults.slAtrMultiplier(), 1e-9);
        assertEquals(2.0, defaults.tpRMultiple(), 1e-9);
        assertEquals(1000, defaults.maxQty());
        assertEquals(3, defaults.maxConsecutiveLosses());
    }

    // ── StrategyOrderConfig: factory from parsed YAML ─────────────────────────

    @Test
    void strategyOrderConfig_fromYamlOrder_allFieldsMapped() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        StrategyYamlConfig cfg = loader.load(yamlPath).get("test_trend");
        StrategyOrderConfig order = StrategyOrderConfig.from(cfg.getOrder());

        assertEquals("MARKET", order.type());
        assertEquals(0.03, order.slippageTolerance(), 1e-9);
        assertEquals("INTRADAY", order.productType());
    }

    @Test
    void strategyOrderConfig_limitOrderType_parsedCorrectly() throws URISyntaxException {
        Path yamlPath = resourcePath("test-strategies.yaml");

        StrategyYamlConfig cfg = loader.load(yamlPath).get("test_disabled");
        StrategyOrderConfig order = StrategyOrderConfig.from(cfg.getOrder());

        assertEquals("LIMIT", order.type());
        assertEquals("INTRADAY", order.productType());
    }

    @Test
    void strategyOrderConfig_defaults_matchExpectedValues() {
        StrategyOrderConfig defaults = StrategyOrderConfig.defaults();

        assertEquals("MARKET", defaults.type());
        assertEquals(0.05, defaults.slippageTolerance(), 1e-9);
        assertEquals("INTRADAY", defaults.productType());
    }

    // ── loadWithDefaults: fallback to defaults.yaml ───────────────────────────

    @Test
    void loadWithDefaults_missingRiskSection_usesDefaultsYamlValues() throws URISyntaxException {
        Path strategiesPath = resourcePath("test-strategies-no-risk.yaml");
        Path defaultsPath = resourcePath("test-defaults-risk.yaml");

        Map<String, StrategyYamlConfig> strategies = loader.loadWithDefaults(strategiesPath, defaultsPath);

        StrategyYamlConfig cfg = strategies.get("no_risk_strat");
        assertNotNull(cfg, "Expected no_risk_strat to be loaded");

        StrategyRiskConfig risk = StrategyRiskConfig.from(cfg.getRisk());
        // Values should come from test-defaults-risk.yaml, not Java hardcoded defaults
        assertEquals(1.0, risk.riskPerTradePct(), 1e-9);
        assertEquals(1.5, risk.slAtrMultiplier(), 1e-9);
        assertEquals(3.0, risk.tpRMultiple(), 1e-9);
        assertEquals(200, risk.maxQty());
        assertEquals(2, risk.maxConsecutiveLosses());
    }

    @Test
    void loadWithDefaults_missingOrderSection_usesDefaultsYamlValues() throws URISyntaxException {
        Path strategiesPath = resourcePath("test-strategies-no-risk.yaml");
        Path defaultsPath = resourcePath("test-defaults-risk.yaml");

        Map<String, StrategyYamlConfig> strategies = loader.loadWithDefaults(strategiesPath, defaultsPath);

        StrategyYamlConfig cfg = strategies.get("no_risk_strat");
        assertNotNull(cfg);

        StrategyOrderConfig order = StrategyOrderConfig.from(cfg.getOrder());
        // Values should come from test-defaults-risk.yaml
        assertEquals("LIMIT", order.type());
        assertEquals(0.01, order.slippageTolerance(), 1e-9);
        assertEquals("CNC", order.productType());
    }

    @Test
    void loadWithDefaults_strategyHasRiskSection_overridesDefaults() throws URISyntaxException {
        Path strategiesPath = resourcePath("test-strategies.yaml");
        Path defaultsPath = resourcePath("test-defaults-risk.yaml");

        Map<String, StrategyYamlConfig> strategies = loader.loadWithDefaults(strategiesPath, defaultsPath);

        StrategyYamlConfig cfg = strategies.get("test_trend");
        assertNotNull(cfg);

        StrategyRiskConfig risk = StrategyRiskConfig.from(cfg.getRisk());
        // test_trend has its own risk section: risk_per_trade_pct: 1.5 (not default 1.0)
        assertEquals(1.5, risk.riskPerTradePct(), 1e-9);
        assertEquals(500, risk.maxQty()); // strategy-specific, not default 200
    }

    @Test
    void loadWithDefaults_missingDefaultsFile_behavesLikeLoad(@TempDir Path tempDir) throws URISyntaxException {
        Path strategiesPath = resourcePath("test-strategies.yaml");
        Path noDefaultsFile = tempDir.resolve("no-defaults.yaml");

        // Should not throw, should load strategies with Java built-in defaults
        Map<String, StrategyYamlConfig> strategies = loader.loadWithDefaults(strategiesPath, noDefaultsFile);

        assertEquals(2, strategies.size());
        assertTrue(strategies.containsKey("test_trend"));
    }

    @Test
    void loadWithDefaults_missingStrategiesFile_returnsEmptyMap(@TempDir Path tempDir) throws URISyntaxException {
        Path noFile = tempDir.resolve("no-strategies.yaml");
        Path defaultsPath = resourcePath("test-defaults-risk.yaml");

        Map<String, StrategyYamlConfig> result = loader.loadWithDefaults(noFile, defaultsPath);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void loadWithDefaults_malformedStrategiesYaml_throwsIllegalArgumentException(
            @TempDir Path tempDir) throws Exception {
        Path malformed = tempDir.resolve("malformed.yaml");
        Files.writeString(malformed, "strategies:\n  bad:\n   - [unclosed");
        Path defaultsPath = resourcePath("test-defaults-risk.yaml");

        assertThrows(IllegalArgumentException.class,
                () -> loader.loadWithDefaults(malformed, defaultsPath));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Path resourcePath(String resourceName) throws URISyntaxException {
        URL url = getClass().getClassLoader().getResource(resourceName);
        assertNotNull(url, "Test resource not found: " + resourceName);
        return Path.of(url.toURI());
    }
}
