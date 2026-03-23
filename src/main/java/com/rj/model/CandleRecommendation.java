package com.rj.model;

import java.time.Instant;

/**
 * Output of a single (symbol × timeframe) candle analysis cycle.
 * Immutable; produced by {@code CandleAnalyzer}, consumed by {@code StrategyEvaluator}.
 */
public final class CandleRecommendation {

    private final String symbol;
    private final Timeframe timeframe;
    private final Instant windowStart;
    private final Instant windowEnd;
    private final Signal signal;
    private final double confidence;     // 0.0 – 1.0
    private final String strategySource; // e.g. "TREND_FOLLOWING"

    // Indicator snapshot at analysis time
    private final double ema20;
    private final double ema50;
    private final double rsi14;
    private final double atr14;
    private final double relVolume;

    private final Candle candle;
    private final Instant generatedAt;

    private CandleRecommendation(Builder b) {
        this.symbol = b.symbol;
        this.timeframe = b.timeframe;
        this.windowStart = b.windowStart;
        this.windowEnd = b.windowEnd;
        this.signal = b.signal;
        this.confidence = b.confidence;
        this.strategySource = b.strategySource;
        this.ema20 = b.ema20;
        this.ema50 = b.ema50;
        this.rsi14 = b.rsi14;
        this.atr14 = b.atr14;
        this.relVolume = b.relVolume;
        this.candle = b.candle;
        this.generatedAt = b.generatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSymbol() {
        return symbol;
    }

    public Timeframe getTimeframe() {
        return timeframe;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public Signal getSignal() {
        return signal;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getStrategySource() {
        return strategySource;
    }

    public double getEma20() {
        return ema20;
    }

    public double getEma50() {
        return ema50;
    }

    public double getRsi14() {
        return rsi14;
    }

    public double getAtr14() {
        return atr14;
    }

    public double getRelVolume() {
        return relVolume;
    }

    public Candle getCandle() {
        return candle;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    @Override
    public String toString() {
        return String.format("CandleRec{%s[%s] %s conf=%.2f src=%s window=%s}",
                symbol, timeframe, signal, confidence, strategySource, windowStart);
    }

    public static final class Builder {
        private String symbol;
        private Timeframe timeframe;
        private Instant windowStart;
        private Instant windowEnd;
        private Signal signal = Signal.HOLD;
        private double confidence = 0.0;
        private String strategySource = "UNKNOWN";
        private double ema20, ema50, rsi14, atr14, relVolume;
        private Candle candle;
        private Instant generatedAt = Instant.now();

        public Builder symbol(String v) {
            symbol = v;
            return this;
        }

        public Builder timeframe(Timeframe v) {
            timeframe = v;
            return this;
        }

        public Builder windowStart(Instant v) {
            windowStart = v;
            return this;
        }

        public Builder windowEnd(Instant v) {
            windowEnd = v;
            return this;
        }

        public Builder signal(Signal v) {
            signal = v;
            return this;
        }

        public Builder confidence(double v) {
            confidence = v;
            return this;
        }

        public Builder strategySource(String v) {
            strategySource = v;
            return this;
        }

        public Builder ema20(double v) {
            ema20 = v;
            return this;
        }

        public Builder ema50(double v) {
            ema50 = v;
            return this;
        }

        public Builder rsi14(double v) {
            rsi14 = v;
            return this;
        }

        public Builder atr14(double v) {
            atr14 = v;
            return this;
        }

        public Builder relVolume(double v) {
            relVolume = v;
            return this;
        }

        public Builder candle(Candle v) {
            candle = v;
            return this;
        }

        public Builder generatedAt(Instant v) {
            generatedAt = v;
            return this;
        }

        public CandleRecommendation build() {
            return new CandleRecommendation(this);
        }
    }
}