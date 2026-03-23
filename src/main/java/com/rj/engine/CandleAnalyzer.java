package com.rj.engine;

import com.rj.model.Candle;
import com.rj.model.CandleRecommendation;
import com.rj.model.Signal;
import com.rj.model.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNum;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Wraps a ta4j {@link BarSeries} for one (symbol × timeframe) pair.
 * Not thread-safe — each {@code CandleWorker} virtual thread owns exactly one instance.
 *
 * <p>Call {@link #addAndAnalyze} each time a completed candle is ready.
 * The method appends the bar to the series, recalculates all indicators,
 * applies the strategy rules, and returns a {@link CandleRecommendation}.</p>
 */
class CandleAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CandleAnalyzer.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Minimum bars required before emitting directional signals
    private static final int MIN_BARS_FOR_SIGNAL = 21;

    private final String symbol;
    private final Timeframe timeframe;
    private final BarSeries series;

    // ta4j indicators
    private final ClosePriceIndicator closePrice;
    private final EMAIndicator ema20;
    private final EMAIndicator ema50;
    private final RSIIndicator rsi14;
    private final ATRIndicator atr14;

    // Rolling 20-bar volume window for relative-volume calculation
    private final double[] volumeWindow = new double[20];
    private int volumeCount = 0;

    CandleAnalyzer(String symbol, Timeframe timeframe) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.series = new BaseBarSeries(symbol + "_" + timeframe.getLabel());
        this.series.setMaximumBarCount(200);

        this.closePrice = new ClosePriceIndicator(series);
        this.ema20 = new EMAIndicator(closePrice, 20);
        this.ema50 = new EMAIndicator(closePrice, 50);
        this.rsi14 = new RSIIndicator(closePrice, 14);
        this.atr14 = new ATRIndicator(series, 14);
    }

    /**
     * Adds the completed candle to the internal series, runs indicator calculations,
     * applies strategy rules, and returns an analysis recommendation.
     *
     * @param candle      the completed OHLCV candle
     * @param windowStart candle open time (inclusive)
     * @param windowEnd   candle close time (exclusive)
     */
    CandleRecommendation addAndAnalyze(Candle candle, Instant windowStart, Instant windowEnd) {
        appendBar(candle, windowEnd);
        return analyze(candle, windowStart, windowEnd);
    }

    int barCount() {
        return series.getBarCount();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void appendBar(Candle candle, Instant windowEnd) {
        ZonedDateTime endTime = ZonedDateTime.ofInstant(windowEnd, IST);
        try {
            Bar bar = new BaseBar(
                    timeframe.getDuration(),
                    endTime,
                    DoubleNum.valueOf(candle.open),
                    DoubleNum.valueOf(candle.high),
                    DoubleNum.valueOf(candle.low),
                    DoubleNum.valueOf(candle.close),
                    DoubleNum.valueOf(candle.volume),
                    DoubleNum.valueOf(0));  // amount — not available from ticks
            series.addBar(bar);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to add bar to series: {}", symbol, timeframe, e.getMessage());
        }

        // Update rolling volume window
        volumeWindow[volumeCount % 20] = candle.volume;
        volumeCount++;
    }

    private CandleRecommendation analyze(Candle candle, Instant windowStart, Instant windowEnd) {
        int last = series.getEndIndex();

        if (last < MIN_BARS_FOR_SIGNAL) {
            log.debug("[{}][{}] Only {} bars — waiting for {}", symbol, timeframe, last + 1, MIN_BARS_FOR_SIGNAL);
            return insufficientData(candle, windowStart, windowEnd);
        }

        double close = candle.close;
        double e20 = ema20.getValue(last).doubleValue();
        double e50 = ema50.getValue(last).doubleValue();
        double rsi = last >= 14 ? rsi14.getValue(last).doubleValue() : 50.0;
        double atr = last >= 14 ? atr14.getValue(last).doubleValue() : 0.0;
        double relVol = computeRelVolume(candle.volume);

        // ── Trend classification ──────────────────────────────────────────────
        boolean bullish = close > e20 && e20 > e50;
        boolean bearish = close < e20 && e20 < e50;
        boolean sideways = !bullish && !bearish;

        // ── Strategy selection (highest-priority wins) ────────────────────────
        Signal signal = Signal.HOLD;
        double confidence = 0.0;
        String source = "NONE";

        // 1. Trend Following (confidence 0.85)
        if (bullish && relVol > 1.2) {
            signal = Signal.BUY;
            confidence = 0.85;
            source = "TREND_FOLLOWING";
        } else if (bearish && relVol > 1.2) {
            signal = Signal.SELL;
            confidence = 0.85;
            source = "TREND_FOLLOWING";
        }

        // 2. Mean Reversion (confidence 0.70) — only applied when sideways
        if (sideways && rsi < 30) {
            signal = Signal.BUY;
            confidence = 0.70;
            source = "MEAN_REVERSION";
        } else if (sideways && rsi > 70) {
            signal = Signal.SELL;
            confidence = 0.70;
            source = "MEAN_REVERSION";
        }

        // 3. Volatility Breakout (confidence 0.90) — overrides when relVol > 2.0
        if (relVol > 2.0 && atr > 0) {
            Signal breakout = bullish ? Signal.BUY : (bearish ? Signal.SELL : Signal.HOLD);
            if (breakout != Signal.HOLD) {
                signal = breakout;
                confidence = 0.90;
                source = "VOLATILITY_BREAKOUT";
            }
        }

        log.debug("[{}][{}] close={} ema20={} ema50={} rsi={} relVol={} → {} conf={}",
                symbol, timeframe,
                String.format("%.2f", close),
                String.format("%.2f", e20),
                String.format("%.2f", e50),
                String.format("%.1f", rsi),
                String.format("%.2f", relVol),
                signal, String.format("%.2f", confidence));

        return CandleRecommendation.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .signal(signal)
                .confidence(confidence)
                .strategySource(source)
                .ema20(e20).ema50(e50).rsi14(rsi).atr14(atr).relVolume(relVol)
                .candle(candle)
                .build();
    }

    private CandleRecommendation insufficientData(Candle candle, Instant ws, Instant we) {
        return CandleRecommendation.builder()
                .symbol(symbol).timeframe(timeframe)
                .windowStart(ws).windowEnd(we)
                .signal(Signal.HOLD).confidence(0.0)
                .strategySource("INSUFFICIENT_DATA")
                .candle(candle).build();
    }

    private double computeRelVolume(long currentVolume) {
        int count = Math.min(volumeCount, 20);
        if (count < 5) return 1.0;
        double sum = 0;
        for (int i = 0; i < count; i++) sum += volumeWindow[i];
        double avg = sum / count;
        return avg > 0 ? (double) currentVolume / avg : 1.0;
    }
}
