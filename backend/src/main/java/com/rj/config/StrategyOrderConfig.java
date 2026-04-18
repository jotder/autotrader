package com.rj.config;

/**
 * Immutable order-execution parameters for a single strategy, extracted from YAML config.
 * Consumed by the OMS layer to control how orders are placed.
 *
 * <p>Valid values:</p>
 * <ul>
 *   <li>{@code type}: {@code MARKET} or {@code LIMIT}</li>
 *   <li>{@code productType}: {@code INTRADAY} or {@code CNC}</li>
 *   <li>{@code slippageTolerance}: fraction of price (e.g. 0.05 = 0.05%)</li>
 * </ul>
 */
public record StrategyOrderConfig(
        String type,               // MARKET or LIMIT
        double slippageTolerance,  // max acceptable slippage as a fraction of price
        String productType         // INTRADAY or CNC
) {

    /**
     * Factory method: builds a {@code StrategyOrderConfig} from a parsed
     * {@link StrategyYamlConfig.Order} section.
     */
    public static StrategyOrderConfig from(StrategyYamlConfig.Order order) {
        return new StrategyOrderConfig(
                order.getType(),
                order.getSlippageTolerance(),
                order.getProductType());
    }

    /** Returns safe built-in defaults. */
    public static StrategyOrderConfig defaults() {
        return new StrategyOrderConfig("MARKET", 0.05, "INTRADAY");
    }
}
