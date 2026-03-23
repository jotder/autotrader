package com.rj.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates {@link StrategyYamlConfig} objects against defined constraints.
 * <p>
 * Rules enforced:
 * <ul>
 *   <li>Required fields: {@code symbols} must not be empty.</li>
 *   <li>Numeric ranges: risk percentages, ATR multipliers, periods, etc.</li>
 *   <li>Enum values: {@code timeframe}, {@code order.type}, {@code order.productType},
 *       {@code entry.trendStrength}.</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 *   ConfigValidator validator = new ConfigValidator();
 *   ValidationResult result = validator.validate("trend_following", config);
 *   if (!result.valid()) {
 *       result.errors().forEach(e -&gt; log.warn("Config error: {}", e));
 *   }
 * </pre>
 */
public class ConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigValidator.class);

    // Valid enum sets — configurable at construction time for testability
    private final Set<String> validTimeframes;
    private final Set<String> validOrderTypes;
    private final Set<String> validProductTypes;
    private final Set<String> validTrendStrengths;

    // Optional symbol registry for cross-validation (null = skip symbol validation)
    private final SymbolRegistry symbolRegistry;

    // Numeric range constants
    private static final double RISK_PCT_MIN     = 0.1;
    private static final double RISK_PCT_MAX     = 10.0;
    private static final double CONFIDENCE_MIN   = 0.0;
    private static final double CONFIDENCE_MAX   = 1.0;
    private static final double PCT_FIELD_MIN    = 0.0;   // for exposure/trailing pcts
    private static final double PCT_FIELD_MAX    = 100.0;
    private static final int    PERIOD_MIN       = 1;
    private static final int    MAX_QTY_MIN      = 1;
    private static final int    MAX_LOSSES_MIN   = 1;
    private static final int    COOLDOWN_MIN     = 0;
    private static final int    MAX_TRADES_MIN   = 1;

    /** Default constructor — no symbol registry validation. */
    public ConfigValidator() {
        this(null);
    }

    /**
     * Constructor with optional symbol registry for cross-validation.
     *
     * @param symbolRegistry if non-null, strategy symbols are validated against the global list
     */
    public ConfigValidator(SymbolRegistry symbolRegistry) {
        this.symbolRegistry     = symbolRegistry;
        this.validTimeframes    = Set.of("M1", "M5", "M15", "H1", "D");
        this.validOrderTypes    = Set.of("MARKET", "LIMIT");
        this.validProductTypes  = Set.of("INTRADAY", "CNC");
        this.validTrendStrengths = Set.of(
                "STRONG_BULLISH", "BULLISH", "NEUTRAL", "BEARISH", "STRONG_BEARISH");
    }

    /**
     * Result of a validation run. Immutable.
     *
     * @param valid  {@code true} if all checks passed
     * @param errors list of human-readable error messages; empty if valid
     */
    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }
        public static ValidationResult fail(List<String> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }
    }

    /**
     * Validates a single strategy config.
     *
     * @param strategyName name of the strategy (used in error messages)
     * @param cfg          the config to validate
     * @return validation result; never {@code null}
     */
    public ValidationResult validate(String strategyName, StrategyYamlConfig cfg) {
        List<String> errors = new ArrayList<>();
        String prefix = "strategy '" + strategyName + "'";

        // ── Required fields ──────────────────────────────────────────────────
        if (cfg.getSymbols() == null || cfg.getSymbols().isEmpty()) {
            errors.add(prefix + ": 'symbols' is required and must not be empty");
        } else if (symbolRegistry != null) {
            // Cross-validate strategy symbols against global registry
            List<String> invalid = symbolRegistry.validateStrategySymbols(strategyName, cfg.getSymbols());
            for (String sym : invalid) {
                errors.add(prefix + ": symbol '" + sym + "' not found in global symbol registry (config/symbols.yaml)");
            }
        }

        // ── Enum: timeframe ──────────────────────────────────────────────────
        if (!validTimeframes.contains(cfg.getTimeframe())) {
            errors.add(prefix + ": invalid timeframe '" + cfg.getTimeframe()
                    + "', must be one of " + validTimeframes);
        }

        // ── Top-level numeric ranges ─────────────────────────────────────────
        if (cfg.getCooldownMinutes() < COOLDOWN_MIN) {
            errors.add(prefix + ": cooldown_minutes must be >= " + COOLDOWN_MIN
                    + " (got " + cfg.getCooldownMinutes() + ")");
        }
        if (cfg.getMaxTradesPerDay() < MAX_TRADES_MIN) {
            errors.add(prefix + ": max_trades_per_day must be >= " + MAX_TRADES_MIN
                    + " (got " + cfg.getMaxTradesPerDay() + ")");
        }

        // ── Indicators ───────────────────────────────────────────────────────
        validateIndicators(prefix, cfg.getIndicators(), errors);

        // ── Entry ────────────────────────────────────────────────────────────
        validateEntry(prefix, cfg.getEntry(), errors);

        // ── Risk ─────────────────────────────────────────────────────────────
        validateRisk(prefix, cfg.getRisk(), errors);

        // ── Order ────────────────────────────────────────────────────────────
        validateOrder(prefix, cfg.getOrder(), errors);

        if (!errors.isEmpty()) {
            errors.forEach(e -> log.warn("Config validation failed: {}", e));
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(errors);
    }

    /**
     * Validates all strategies in the map. Returns a combined result.
     *
     * @param strategies map of strategy name → config
     * @return combined validation result; valid only if all strategies pass
     */
    public ValidationResult validateAll(Map<String, StrategyYamlConfig> strategies) {
        List<String> allErrors = new ArrayList<>();
        for (Map.Entry<String, StrategyYamlConfig> entry : strategies.entrySet()) {
            ValidationResult r = validate(entry.getKey(), entry.getValue());
            if (!r.valid()) {
                allErrors.addAll(r.errors());
            }
        }
        return allErrors.isEmpty() ? ValidationResult.ok() : ValidationResult.fail(allErrors);
    }

    // ── Private section validators ────────────────────────────────────────────

    private void validateIndicators(String prefix, StrategyYamlConfig.Indicators ind,
                                    List<String> errors) {
        if (ind.getEmaFast() < PERIOD_MIN) {
            errors.add(prefix + ": indicators.ema_fast must be >= " + PERIOD_MIN
                    + " (got " + ind.getEmaFast() + ")");
        }
        if (ind.getEmaSlow() < PERIOD_MIN) {
            errors.add(prefix + ": indicators.ema_slow must be >= " + PERIOD_MIN
                    + " (got " + ind.getEmaSlow() + ")");
        }
        if (ind.getEmaFast() >= ind.getEmaSlow()) {
            errors.add(prefix + ": indicators.ema_fast (" + ind.getEmaFast()
                    + ") must be less than ema_slow (" + ind.getEmaSlow() + ")");
        }
        if (ind.getRsiPeriod() < PERIOD_MIN) {
            errors.add(prefix + ": indicators.rsi_period must be >= " + PERIOD_MIN
                    + " (got " + ind.getRsiPeriod() + ")");
        }
        if (ind.getAtrPeriod() < PERIOD_MIN) {
            errors.add(prefix + ": indicators.atr_period must be >= " + PERIOD_MIN
                    + " (got " + ind.getAtrPeriod() + ")");
        }
        if (ind.getRelVolPeriod() < PERIOD_MIN) {
            errors.add(prefix + ": indicators.rel_vol_period must be >= " + PERIOD_MIN
                    + " (got " + ind.getRelVolPeriod() + ")");
        }
        if (ind.getMinCandles() < PERIOD_MIN) {
            errors.add(prefix + ": indicators.min_candles must be >= " + PERIOD_MIN
                    + " (got " + ind.getMinCandles() + ")");
        }
    }

    private void validateEntry(String prefix, StrategyYamlConfig.Entry entry,
                               List<String> errors) {
        if (entry.getMinConfidence() < CONFIDENCE_MIN || entry.getMinConfidence() > CONFIDENCE_MAX) {
            errors.add(prefix + ": entry.min_confidence must be in [" + CONFIDENCE_MIN
                    + ", " + CONFIDENCE_MAX + "] (got " + entry.getMinConfidence() + ")");
        }
        if (entry.getRelVolThreshold() <= 0.0) {
            errors.add(prefix + ": entry.rel_vol_threshold must be > 0 (got "
                    + entry.getRelVolThreshold() + ")");
        }
        if (!validTrendStrengths.contains(entry.getTrendStrength())) {
            errors.add(prefix + ": invalid entry.trend_strength '" + entry.getTrendStrength()
                    + "', must be one of " + validTrendStrengths);
        }
    }

    private void validateRisk(String prefix, StrategyYamlConfig.Risk risk, List<String> errors) {
        if (risk.getRiskPerTradePct() < RISK_PCT_MIN || risk.getRiskPerTradePct() > RISK_PCT_MAX) {
            errors.add(prefix + ": risk.risk_per_trade_pct must be in ["
                    + RISK_PCT_MIN + ", " + RISK_PCT_MAX + "] (got " + risk.getRiskPerTradePct() + ")");
        }
        if (risk.getSlAtrMultiplier() <= 0.0) {
            errors.add(prefix + ": risk.sl_atr_multiplier must be > 0 (got "
                    + risk.getSlAtrMultiplier() + ")");
        }
        if (risk.getTpRMultiple() <= 0.0) {
            errors.add(prefix + ": risk.tp_r_multiple must be > 0 (got "
                    + risk.getTpRMultiple() + ")");
        }
        if (risk.getTrailingActivationPct() < PCT_FIELD_MIN
                || risk.getTrailingActivationPct() > PCT_FIELD_MAX) {
            errors.add(prefix + ": risk.trailing_activation_pct must be in ["
                    + PCT_FIELD_MIN + ", " + PCT_FIELD_MAX + "] (got "
                    + risk.getTrailingActivationPct() + ")");
        }
        if (risk.getTrailingStepPct() < PCT_FIELD_MIN
                || risk.getTrailingStepPct() > PCT_FIELD_MAX) {
            errors.add(prefix + ": risk.trailing_step_pct must be in ["
                    + PCT_FIELD_MIN + ", " + PCT_FIELD_MAX + "] (got "
                    + risk.getTrailingStepPct() + ")");
        }
        if (risk.getMaxExposurePct() < PCT_FIELD_MIN || risk.getMaxExposurePct() > PCT_FIELD_MAX) {
            errors.add(prefix + ": risk.max_exposure_pct must be in ["
                    + PCT_FIELD_MIN + ", " + PCT_FIELD_MAX + "] (got "
                    + risk.getMaxExposurePct() + ")");
        }
        if (risk.getMaxQty() < MAX_QTY_MIN) {
            errors.add(prefix + ": risk.max_qty must be >= " + MAX_QTY_MIN
                    + " (got " + risk.getMaxQty() + ")");
        }
        if (risk.getMaxConsecutiveLosses() < MAX_LOSSES_MIN) {
            errors.add(prefix + ": risk.max_consecutive_losses must be >= " + MAX_LOSSES_MIN
                    + " (got " + risk.getMaxConsecutiveLosses() + ")");
        }
    }

    private void validateOrder(String prefix, StrategyYamlConfig.Order order, List<String> errors) {
        if (!validOrderTypes.contains(order.getType())) {
            errors.add(prefix + ": invalid order.type '" + order.getType()
                    + "', must be one of " + validOrderTypes);
        }
        if (!validProductTypes.contains(order.getProductType())) {
            errors.add(prefix + ": invalid order.product_type '" + order.getProductType()
                    + "', must be one of " + validProductTypes);
        }
        if (order.getSlippageTolerance() < 0.0) {
            errors.add(prefix + ": order.slippage_tolerance must be >= 0 (got "
                    + order.getSlippageTolerance() + ")");
        }
    }
}
