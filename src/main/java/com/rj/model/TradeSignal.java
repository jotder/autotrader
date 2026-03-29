package com.rj.model;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * A compound entry signal ready for risk evaluation and order placement.
 * Produced by {@code StrategyEvaluator} after aggregating multi-timeframe votes.
 * Immutable.
 */
public final class TradeSignal {

    private final String symbol;
    private final String correlationId;    // stable fingerprint; used for idempotency
    private final Signal direction;
    private final double confidence;       // numeric score 0.0-1.0
    private final Confidence confidenceLevel; // Phase-II Enum
    private final String reason;           // Logical justification
    private final double atr;              // Volatility at time of signal
    private final double suggestedEntry;
    private final double suggestedStopLoss;
    private final double suggestedTarget;
    private final String strategyId;
    private final Map<Timeframe, Signal> timeframeVotes;  // M5→BUY, M15→BUY, H1→BUY etc.
    private final Instant generatedAt;
    private final InstrumentInfo instrumentInfo;          // nullable for backward compat

    private TradeSignal(Builder b) {
        this.symbol = b.symbol;
        this.correlationId = b.correlationId;
        this.direction = b.direction;
        this.confidence = b.confidence;
        this.confidenceLevel = b.confidenceLevel != null ? b.confidenceLevel : Confidence.NORMAL;
        this.reason = b.reason != null ? b.reason : "No reason provided";
        this.atr = b.atr;
        this.suggestedEntry = b.suggestedEntry;
        this.suggestedStopLoss = b.suggestedStopLoss;
        this.suggestedTarget = b.suggestedTarget;
        this.strategyId = b.strategyId;
        this.timeframeVotes = Collections.unmodifiableMap(new EnumMap<>(b.timeframeVotes));
        this.generatedAt = b.generatedAt;
        this.instrumentInfo = b.instrumentInfo;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Signal getDirection() {
        return direction;
    }

    public double getConfidence() {
        return confidence;
    }

    public Confidence getConfidenceLevel() {
        return confidenceLevel;
    }

    public String getReason() {
        return reason;
    }

    public double getAtr() {
        return atr;
    }

    public double getSuggestedEntry() {
        return suggestedEntry;
    }

    public double getSuggestedStopLoss() {
        return suggestedStopLoss;
    }

    public double getSuggestedTarget() {
        return suggestedTarget;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public Map<Timeframe, Signal> getTimeframeVotes() {
        return timeframeVotes;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public InstrumentInfo getInstrumentInfo() {
        return instrumentInfo;
    }

    /** Product type for order placement (INTRADAY, MARGIN, CNC). Defaults to INTRADAY if no instrument info. */
    public String getProductType() {
        return instrumentInfo != null ? instrumentInfo.productType() : "INTRADAY";
    }

    /** Lot size for position sizing. Defaults to 1 if no instrument info. */
    public int getLotSize() {
        return instrumentInfo != null ? instrumentInfo.lotSize() : 1;
    }

    /** Estimated R-multiple: (target - entry) / (entry - stopLoss), direction-adjusted. */
    public double rMultiple() {
        double risk = Math.abs(suggestedEntry - suggestedStopLoss);
        double reward = Math.abs(suggestedTarget - suggestedEntry);
        return risk > 0 ? reward / risk : 0;
    }

    @Override
    public String toString() {
        return String.format("TradeSignal{%s %s conf=%.2f entry=%.2f sl=%.2f tp=%.2f R=%.1f votes=%s}",
                symbol, direction, confidence, suggestedEntry,
                suggestedStopLoss, suggestedTarget, rMultiple(), timeframeVotes);
    }

    public static final class Builder {
        private String symbol;
        private String correlationId;
        private Signal direction;
        private double confidence;
        private double suggestedEntry;
        private double suggestedStopLoss;
        private double suggestedTarget;
        private String strategyId;
        private Confidence confidenceLevel = Confidence.NORMAL;
        private String reason = "No reason provided";
        private double atr;
        private Map<Timeframe, Signal> timeframeVotes = new EnumMap<>(Timeframe.class);
        private Instant generatedAt = Instant.now();
        private InstrumentInfo instrumentInfo;

        public Builder symbol(String v) {
            symbol = v;
            return this;
        }

        public Builder correlationId(String v) {
            correlationId = v;
            return this;
        }

        public Builder direction(Signal v) {
            direction = v;
            return this;
        }

        public Builder confidence(double v) {
            confidence = v;
            return this;
        }

        public Builder confidenceLevel(Confidence v) {
            confidenceLevel = v;
            return this;
        }

        public Builder reason(String v) {
            reason = v;
            return this;
        }

        public Builder atr(double v) {
            this.atr = v;
            return this;
        }

        public Builder suggestedEntry(double v) {
            suggestedEntry = v;
            return this;
        }

        public Builder suggestedStopLoss(double v) {
            suggestedStopLoss = v;
            return this;
        }

        public Builder suggestedTarget(double v) {
            suggestedTarget = v;
            return this;
        }

        public Builder strategyId(String v) {
            strategyId = v;
            return this;
        }

        public Builder vote(Timeframe tf, Signal s) {
            timeframeVotes.put(tf, s);
            return this;
        }

        public Builder generatedAt(Instant v) {
            generatedAt = v;
            return this;
        }

        public Builder instrumentInfo(InstrumentInfo v) {
            instrumentInfo = v;
            return this;
        }

        public TradeSignal build() {
            return new TradeSignal(this);
        }
    }
}
