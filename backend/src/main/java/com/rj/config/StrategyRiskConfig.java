package com.rj.config;

/**
 * Immutable per-strategy risk parameters extracted from YAML config.
 * Used by {@link com.rj.engine.RiskManager} to apply per-strategy overrides
 * to global risk settings from {@link RiskConfig}.
 *
 * <p>All percentage values are stored as plain percentages (e.g. 2.0 = 2%).
 * Callers dividing into fraction form must divide by 100.</p>
 */
public record StrategyRiskConfig(
        double riskPerTradePct,        // % of capital to risk per trade (e.g. 2.0 = 2%)
        double slAtrMultiplier,        // ATR multiplier for stop-loss distance
        double tpRMultiple,            // R-multiple for take-profit
        double trailingActivationPct,  // profit % to activate trailing stop
        double trailingStepPct,        // trailing stop step size %
        double maxExposurePct,         // max symbol exposure as % of capital
        int maxQty,                    // hard cap on order quantity
        int maxConsecutiveLosses       // consecutive losses before strategy suspension
) {

    /**
     * Factory method: builds a {@code StrategyRiskConfig} from a parsed
     * {@link StrategyYamlConfig.Risk} section.
     */
    public static StrategyRiskConfig from(StrategyYamlConfig.Risk risk) {
        return new StrategyRiskConfig(
                risk.getRiskPerTradePct(),
                risk.getSlAtrMultiplier(),
                risk.getTpRMultiple(),
                risk.getTrailingActivationPct(),
                risk.getTrailingStepPct(),
                risk.getMaxExposurePct(),
                risk.getMaxQty(),
                risk.getMaxConsecutiveLosses());
    }

    /** Returns safe built-in defaults matching {@link RiskConfig#defaults()}. */
    public static StrategyRiskConfig defaults() {
        return new StrategyRiskConfig(2.0, 2.0, 2.0, 1.0, 1.0, 20.0, 1000, 3);
    }
}
