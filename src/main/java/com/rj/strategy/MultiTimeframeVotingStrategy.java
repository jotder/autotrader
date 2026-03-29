package com.rj.strategy;

import com.rj.model.*;
import com.rj.config.RiskConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Port of the original Phase-I voting logic into the new pluggable interface.
 */
public class MultiTimeframeVotingStrategy implements ITradeStrategy {
    private static final Logger log = LoggerFactory.getLogger(MultiTimeframeVotingStrategy.class);

    private final String id;
    private final String name;
    private final double minConfidence;
    private final double slAtrMultiplier;
    private final double tpRMultiple;

    public MultiTimeframeVotingStrategy(String id, String name, double minConfidence, double slAtrMultiplier, double tpRMultiple) {
        this.id = id;
        this.name = name;
        this.minConfidence = minConfidence;
        this.slAtrMultiplier = slAtrMultiplier;
        this.tpRMultiple = tpRMultiple;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getName() { return name; }

    @Override
    public Optional<TradeSignal> evaluate(String symbol, Map<Timeframe, CandleRecommendation> votes) {
        CandleRecommendation m5 = votes.get(Timeframe.M5);
        CandleRecommendation m15 = votes.get(Timeframe.M15);
        CandleRecommendation h1 = votes.get(Timeframe.H1);

        if (m5 == null || m15 == null) return Optional.empty();

        Signal m5Signal = m5.getSignal();
        if (!m5Signal.isDirectional()) return Optional.empty();

        // M15 must agree
        if (m15.getSignal() != m5Signal) return Optional.empty();

        // H1 must not oppose
        Signal h1Signal = h1 != null ? h1.getSignal() : Signal.HOLD;
        if (h1Signal.isDirectional() && h1Signal != m5Signal) return Optional.empty();

        double confidence = combineConfidence(m5, m15, h1, m5Signal);
        if (confidence < minConfidence) return Optional.empty();

        double entry = m5.getCandle().close;
        double atrValue = m5.getAtr14() > 0 ? m5.getAtr14() : entry * 0.01;

        double sl = m5Signal == Signal.BUY
                ? entry - (slAtrMultiplier * atrValue)
                : entry + (slAtrMultiplier * atrValue);
        double tp = m5Signal == Signal.BUY
                ? entry + (slAtrMultiplier * tpRMultiple * atrValue)
                : entry - (slAtrMultiplier * tpRMultiple * atrValue);

        // Determine Confidence Enum for sizing
        Confidence level = confidence >= 0.9 ? Confidence.VERY_HIGH :
                          confidence >= 0.8 ? Confidence.HIGH : Confidence.NORMAL;

        String correlationId = symbol + "_" + id + "_" + m5.getWindowStart().getEpochSecond();

        return Optional.of(TradeSignal.builder()
                .symbol(symbol)
                .correlationId(correlationId)
                .direction(m5Signal)
                .confidence(confidence)
                .confidenceLevel(level)
                .reason("M5/M15/H1 aligned voting")
                .atr(atrValue)
                .suggestedEntry(entry)
                .suggestedStopLoss(sl)
                .suggestedTarget(tp)
                .strategyId(id)
                .vote(Timeframe.M5, m5Signal)
                .vote(Timeframe.M15, m15.getSignal())
                .vote(Timeframe.H1, h1Signal)
                .instrumentInfo(m5.getInstrumentInfo())
                .build());
    }

    private double combineConfidence(CandleRecommendation m5, CandleRecommendation m15, CandleRecommendation h1, Signal direction) {
        double base = (m5.getConfidence() + m15.getConfidence()) / 2.0;
        double boost = (h1 != null && h1.getSignal() == direction) ? 0.05 : 0.0;
        return Math.min(1.0, base + boost);
    }
}
