package com.rj.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and parses YAML strategy config files into typed {@link StrategyYamlConfig} objects.
 * <p>
 * Usage:
 * <pre>
 *   YamlStrategyLoader loader = new YamlStrategyLoader();
 *   Map&lt;String, StrategyYamlConfig&gt; strategies = loader.load(Path.of("config/strategies/intraday.yaml"));
 * </pre>
 * <p>
 * Contract:
 * <ul>
 *   <li>Missing file: logs WARN and returns an empty map (does not throw).</li>
 *   <li>Malformed YAML: throws {@link IllegalArgumentException}.</li>
 *   <li>Missing individual fields: silently uses defaults defined in {@link StrategyYamlConfig}.</li>
 *   <li>Rollback: {@link #reloadWithRollback} keeps the last-valid config when a new load fails.</li>
 * </ul>
 */
public class YamlStrategyLoader {

    private static final Logger log = LoggerFactory.getLogger(YamlStrategyLoader.class);

    /** Last known-good config — retained on validation failure or parse error. */
    private volatile Map<String, StrategyYamlConfig> lastValidConfig = Collections.emptyMap();

    /**
     * Loads all strategy configs from the given YAML file.
     *
     * @param filePath path to a YAML file containing a top-level {@code strategies:} map
     * @return immutable map of strategy name → config; empty if file not found
     * @throws IllegalArgumentException if the file exists but has invalid YAML structure
     */
    public Map<String, StrategyYamlConfig> load(Path filePath) {
        if (!Files.exists(filePath)) {
            log.warn("Strategy YAML file not found, skipping: {}", filePath);
            return Collections.emptyMap();
        }

        try (InputStream is = Files.newInputStream(filePath)) {
            Yaml yaml = new Yaml();
            Object raw = yaml.load(is);

            if (raw == null) {
                log.warn("Strategy YAML file is empty: {}", filePath);
                return Collections.emptyMap();
            }

            if (!(raw instanceof Map)) {
                throw new IllegalArgumentException("Expected YAML root to be a map in: " + filePath);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) raw;

            if (!root.containsKey("strategies")) {
                log.warn("No 'strategies' key found in: {}", filePath);
                return Collections.emptyMap();
            }

            Object strategiesRaw = root.get("strategies");
            if (!(strategiesRaw instanceof Map)) {
                throw new IllegalArgumentException("'strategies' must be a map in: " + filePath);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> strategiesMap = (Map<String, Object>) strategiesRaw;

            Map<String, StrategyYamlConfig> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : strategiesMap.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (!(value instanceof Map)) {
                    log.warn("Strategy '{}' has no body, skipping", name);
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> stratMap = (Map<String, Object>) value;
                result.put(name, parseStrategy(name, stratMap));
                log.debug("Parsed strategy config: {}", name);
            }

            log.info("Loaded {} strategies from {}", result.size(), filePath.getFileName());
            return Collections.unmodifiableMap(result);

        } catch (YAMLException e) {
            throw new IllegalArgumentException("Malformed YAML in: " + filePath + " — " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read YAML file: " + filePath, e);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Unexpected YAML structure in: " + filePath, e);
        }
    }

    /**
     * Loads global defaults from a defaults YAML file.
     * Expected structure: top-level {@code defaults:} key.
     *
     * @param filePath path to defaults.yaml
     * @return a {@link StrategyYamlConfig} populated from the {@code defaults} block,
     *         or a config with all-default values if the file is missing
     */
    public StrategyYamlConfig loadDefaults(Path filePath) {
        if (!Files.exists(filePath)) {
            log.warn("Defaults YAML not found, using built-in defaults: {}", filePath);
            return new StrategyYamlConfig();
        }

        try (InputStream is = Files.newInputStream(filePath)) {
            Yaml yaml = new Yaml();
            Object raw = yaml.load(is);

            if (raw == null || !(raw instanceof Map)) {
                log.warn("Defaults YAML is empty or not a map: {}", filePath);
                return new StrategyYamlConfig();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) raw;

            if (!root.containsKey("defaults") || !(root.get("defaults") instanceof Map)) {
                log.warn("No 'defaults' map found in: {}", filePath);
                return new StrategyYamlConfig();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> defaultsMap = (Map<String, Object>) root.get("defaults");
            StrategyYamlConfig defaults = parseStrategy("defaults", defaultsMap);
            log.info("Loaded global defaults from {}", filePath.getFileName());
            return defaults;

        } catch (YAMLException e) {
            throw new IllegalArgumentException("Malformed YAML in: " + filePath + " — " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read defaults file: " + filePath, e);
        }
    }

    /**
     * Loads strategies from {@code strategiesPath}, merging each strategy with
     * global defaults loaded from {@code defaultsPath}.
     *
     * <p>The merge is a deep map overlay: values present in a strategy block take
     * precedence; any missing sections or fields fall back to the defaults block.
     * If {@code defaultsPath} does not exist, behaviour is identical to
     * {@link #load(Path)}.</p>
     *
     * @param strategiesPath path to a strategies YAML file
     * @param defaultsPath   path to defaults.yaml
     * @return immutable map of strategy name → config with defaults applied
     */
    public Map<String, StrategyYamlConfig> loadWithDefaults(Path strategiesPath, Path defaultsPath) {
        Map<String, Object> defaultsMap = readRawDefaults(defaultsPath);

        if (!Files.exists(strategiesPath)) {
            log.warn("Strategy YAML file not found, skipping: {}", strategiesPath);
            return Collections.emptyMap();
        }

        try (InputStream is = Files.newInputStream(strategiesPath)) {
            Yaml yaml = new Yaml();
            Object raw = yaml.load(is);

            if (raw == null) {
                log.warn("Strategy YAML file is empty: {}", strategiesPath);
                return Collections.emptyMap();
            }

            if (!(raw instanceof Map)) {
                throw new IllegalArgumentException("Expected YAML root to be a map in: " + strategiesPath);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) raw;

            if (!root.containsKey("strategies")) {
                log.warn("No 'strategies' key found in: {}", strategiesPath);
                return Collections.emptyMap();
            }

            Object strategiesRaw = root.get("strategies");
            if (!(strategiesRaw instanceof Map)) {
                throw new IllegalArgumentException("'strategies' must be a map in: " + strategiesPath);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> strategiesMap = (Map<String, Object>) strategiesRaw;

            Map<String, StrategyYamlConfig> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : strategiesMap.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (!(value instanceof Map)) {
                    log.warn("Strategy '{}' has no body, skipping", name);
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> stratMap = (Map<String, Object>) value;
                Map<String, Object> merged = deepMerge(defaultsMap, stratMap);
                result.put(name, parseStrategy(name, merged));
                log.debug("Parsed strategy config (with defaults): {}", name);
            }

            log.info("Loaded {} strategies (with defaults) from {}", result.size(), strategiesPath.getFileName());
            return Collections.unmodifiableMap(result);

        } catch (YAMLException e) {
            throw new IllegalArgumentException("Malformed YAML in: " + strategiesPath + " — " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read YAML file: " + strategiesPath, e);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Unexpected YAML structure in: " + strategiesPath, e);
        }
    }

    // ── Validated reload with rollback ───────────────────────────────────────

    /**
     * Attempts to reload strategies from {@code strategiesPath} (merging with defaults).
     * The loaded config is validated by the supplied {@link ConfigValidator}.
     * <p>
     * If validation passes: the new config is stored as the last-valid config and returned.<br>
     * If validation fails or parsing throws: the previous last-valid config is retained and
     * returned unchanged, with a WARN log showing the rejection reason.
     *
     * @param strategiesPath path to strategies YAML file
     * @param defaultsPath   path to defaults YAML file
     * @param validator      validator instance
     * @return the current valid config (new on success, previous on failure)
     */
    public Map<String, StrategyYamlConfig> reloadWithRollback(
            Path strategiesPath, Path defaultsPath, ConfigValidator validator) {

        try {
            Map<String, StrategyYamlConfig> loaded = loadWithDefaults(strategiesPath, defaultsPath);
            ConfigValidator.ValidationResult result = validator.validateAll(loaded);
            if (result.valid()) {
                lastValidConfig = loaded;
                log.info("Config reloaded successfully ({} strategies)", loaded.size());
                return loaded;
            } else {
                log.warn("Config reload rejected — {} validation error(s); retaining previous config",
                        result.errors().size());
                return lastValidConfig;
            }
        } catch (IllegalArgumentException e) {
            log.warn("Config reload failed (malformed YAML): {}; retaining previous config",
                    e.getMessage());
            return lastValidConfig;
        }
    }

    /**
     * Returns the most recently accepted (valid) config, or an empty map if no valid
     * config has been loaded yet.
     */
    public Map<String, StrategyYamlConfig> getLastValidConfig() {
        return lastValidConfig;
    }

    // ── Private parsing helpers ───────────────────────────────────────────────

    /**
     * Reads the raw {@code defaults:} map from a defaults YAML file.
     * Returns an empty map if the file does not exist or has no {@code defaults} key.
     */
    private Map<String, Object> readRawDefaults(Path defaultsPath) {
        if (!Files.exists(defaultsPath)) {
            return Collections.emptyMap();
        }
        try (InputStream is = Files.newInputStream(defaultsPath)) {
            Yaml yaml = new Yaml();
            Object raw = yaml.load(is);
            if (raw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> root = (Map<String, Object>) raw;
                Object defaults = root.get("defaults");
                if (defaults instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> defaultsMap = (Map<String, Object>) defaults;
                    return defaultsMap;
                }
            }
        } catch (IOException | YAMLException e) {
            log.warn("Could not read defaults YAML {}: {}", defaultsPath, e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * Deep-merges two maps: {@code base} provides fallback values,
     * {@code overlay} takes precedence. Nested {@code Map} values are merged
     * recursively; all other value types are overwritten by {@code overlay}.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> overlay) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> e : overlay.entrySet()) {
            Object baseVal = result.get(e.getKey());
            if (baseVal instanceof Map && e.getValue() instanceof Map) {
                result.put(e.getKey(),
                        deepMerge((Map<String, Object>) baseVal, (Map<String, Object>) e.getValue()));
            } else {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    private StrategyYamlConfig parseStrategy(String name, Map<String, Object> map) {
        StrategyYamlConfig cfg = new StrategyYamlConfig();

        cfg.setEnabled(getBool(map, "enabled", true));
        cfg.setSymbols(getStringList(map, "symbols"));
        cfg.setTimeframe(getString(map, "timeframe", "M5"));
        cfg.setCooldownMinutes(getInt(map, "cooldown_minutes", 25));
        cfg.setMaxTradesPerDay(getInt(map, "max_trades_per_day", 10));

        Object ahRaw = map.get("active_hours");
        if (ahRaw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ahMap = (Map<String, Object>) ahRaw;
            StrategyYamlConfig.ActiveHours ah = new StrategyYamlConfig.ActiveHours();
            ah.setStart(getString(ahMap, "start", "09:15"));
            ah.setEnd(getString(ahMap, "end", "15:00"));
            cfg.setActiveHours(ah);
        }

        Object indRaw = map.get("indicators");
        if (indRaw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> indMap = (Map<String, Object>) indRaw;
            StrategyYamlConfig.Indicators ind = new StrategyYamlConfig.Indicators();
            ind.setEmaFast(getInt(indMap, "ema_fast", 20));
            ind.setEmaSlow(getInt(indMap, "ema_slow", 50));
            ind.setRsiPeriod(getInt(indMap, "rsi_period", 14));
            ind.setAtrPeriod(getInt(indMap, "atr_period", 14));
            ind.setRelVolPeriod(getInt(indMap, "rel_vol_period", 20));
            ind.setMinCandles(getInt(indMap, "min_candles", 21));
            cfg.setIndicators(ind);
        }

        Object entryRaw = map.get("entry");
        if (entryRaw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> entryMap = (Map<String, Object>) entryRaw;
            StrategyYamlConfig.Entry entry = new StrategyYamlConfig.Entry();
            entry.setMinConfidence(getDouble(entryMap, "min_confidence", 0.85));
            entry.setRelVolThreshold(getDouble(entryMap, "rel_vol_threshold", 1.2));
            entry.setTrendStrength(getString(entryMap, "trend_strength", "STRONG_BULLISH"));
            cfg.setEntry(entry);
        }

        Object riskRaw = map.get("risk");
        if (riskRaw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> riskMap = (Map<String, Object>) riskRaw;
            StrategyYamlConfig.Risk risk = new StrategyYamlConfig.Risk();
            risk.setRiskPerTradePct(getDouble(riskMap, "risk_per_trade_pct", 2.0));
            risk.setSlAtrMultiplier(getDouble(riskMap, "sl_atr_multiplier", 2.0));
            risk.setTpRMultiple(getDouble(riskMap, "tp_r_multiple", 2.0));
            risk.setTrailingActivationPct(getDouble(riskMap, "trailing_activation_pct", 1.0));
            risk.setTrailingStepPct(getDouble(riskMap, "trailing_step_pct", 1.0));
            risk.setMaxExposurePct(getDouble(riskMap, "max_exposure_pct", 20.0));
            risk.setMaxQty(getInt(riskMap, "max_qty", 1000));
            risk.setMaxConsecutiveLosses(getInt(riskMap, "max_consecutive_losses", 3));
            cfg.setRisk(risk);
        }

        Object orderRaw = map.get("order");
        if (orderRaw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> orderMap = (Map<String, Object>) orderRaw;
            StrategyYamlConfig.Order order = new StrategyYamlConfig.Order();
            order.setType(getString(orderMap, "type", "MARKET"));
            order.setSlippageTolerance(getDouble(orderMap, "slippage_tolerance", 0.05));
            order.setProductType(getString(orderMap, "product_type", "INTRADAY"));
            cfg.setOrder(order);
        }

        return cfg;
    }

    private boolean getBool(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        return def;
    }

    private String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        if (val instanceof String s) return s;
        return def;
    }

    private int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return def;
    }

    private double getDouble(Map<String, Object> map, String key, double def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return def;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> list) {
            return list.stream()
                    .filter(e -> e instanceof String)
                    .map(e -> (String) e)
                    .toList();
        }
        return List.of();
    }
}
