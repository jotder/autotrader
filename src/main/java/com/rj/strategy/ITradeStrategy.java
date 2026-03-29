package com.rj.strategy;

import com.rj.model.CandleRecommendation;
import com.rj.model.Timeframe;
import com.rj.model.TradeSignal;

import java.util.Map;
import java.util.Optional;

/**
 * Interface for pluggable trading strategies in Phase-II.
 */
public interface ITradeStrategy {

    /**
     * Unique identifier for the strategy.
     */
    String getId();

    /**
     * Descriptive name.
     */
    String getName();

    /**
     * Evaluates a symbol based on its timeframe-aligned recommendations.
     * @param symbol The symbol to evaluate (e.g. NSE:SBIN-EQ)
     * @param history The latest recommendations per timeframe for this symbol.
     * @return An optional TradeSignal if the strategy conditions are met.
     */
    Optional<TradeSignal> evaluate(String symbol, Map<Timeframe, CandleRecommendation> history);

    /**
     * Optional hook for processing individual candle updates if needed
     * for stateful strategies.
     */
    default void onCandle(CandleRecommendation recommendation) {
        // Default: no-op
    }
}
