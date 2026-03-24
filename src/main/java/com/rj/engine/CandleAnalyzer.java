package com.rj.engine;

import com.rj.config.StrategyYamlConfig;
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
import org.ta4j.core.indicators.MACDIndicator;
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
 *
 * <p>Indicator periods and entry thresholds can be configured via
 * {@link StrategyYamlConfig.Indicators} and {@link StrategyYamlConfig.Entry}.
 * When no config is provided, defaults match the original hardcoded values.</p>
 */
class CandleAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CandleAnalyzer.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final String symbol;
    private final Timeframe timeframe;
    private final BarSeries series;

    // Configurable indicator periods (from YAML or defaults)
    private final int emaFastPeriod;
    private final int emaSlowPeriod;
    private final int rsiPeriod;
    private final int atrPeriod;
    private final int relVolPeriod;
    private final int minBarsForSignal;

    // Configurable entry thresholds (from YAML or defaults)
    private final double relVolThreshold;

    // ta4j indicators
    private final EMAIndicator emaFast;
    private final EMAIndicator emaSlow;
    private final RSIIndicator rsi;
    private final ATRIndicator atr;
    private final MACDIndicator macd;
    private final EMAIndicator macdSignal;

    // Rolling volume window for relative-volume calculation
    private final double[] volumeWindow;
    private int volumeCount = 0;

    /**
     * Creates a CandleAnalyzer with default indicator periods.
     * Backward-compatible constructor used when no YAML config is available.
     */
    CandleAnalyzer(String symbol, Timeframe timeframe) {
        this(symbol, timeframe, new StrategyYamlConfig.Indicators(), new StrategyYamlConfig.Entry());
    }

    /**
     * Creates a CandleAnalyzer with indicator periods and entry thresholds from YAML config.
     *
     * @param symbol     the trading symbol
     * @param timeframe  the candle timeframe
     * @param indicators indicator period configuration (EMA, RSI, ATR, etc.)
     * @param entry      entry threshold configuration (relVol threshold, confidence, etc.)
     */
    CandleAnalyzer(String symbol, Timeframe timeframe,
                   StrategyYamlConfig.Indicators indicators,
                   StrategyYamlConfig.Entry entry) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.series = new BaseBarSeries(symbol + "_" + timeframe.getLabel());
        this.series.setMaximumBarCount(200);

        // Store configurable periods
        this.emaFastPeriod = indicators.getEmaFast();
        this.emaSlowPeriod = indicators.getEmaSlow();
        this.rsiPeriod = indicators.getRsiPeriod();
        this.atrPeriod = indicators.getAtrPeriod();
        this.relVolPeriod = indicators.getRelVolPeriod();
        this.minBarsForSignal = indicators.getMinCandles();

        // Entry thresholds
        this.relVolThreshold = entry.getRelVolThreshold();

        // Rolling volume window sized from config
        this.volumeWindow = new double[this.relVolPeriod];

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        this.emaFast = new EMAIndicator(closePrice, emaFastPeriod);
        this.emaSlow = new EMAIndicator(closePrice, emaSlowPeriod);
        this.rsi = new RSIIndicator(closePrice, rsiPeriod);
        this.atr = new ATRIndicator(series, atrPeriod);
        this.macd = new MACDIndicator(closePrice, 12, 26);
        this.macdSignal = new EMAIndicator(macd, 9);

        log.info("[{}][{}] CandleAnalyzer initialized: emaFast={} emaSlow={} rsi={} atr={} relVol={} minBars={}",
                symbol, timeframe, emaFastPeriod, emaSlowPeriod, rsiPeriod, atrPeriod, relVolPeriod, minBarsForSignal);
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
        volumeWindow[volumeCount % relVolPeriod] = candle.volume;
        volumeCount++;
    }

    private CandleRecommendation analyze(Candle candle, Instant windowStart, Instant windowEnd) {
        int last = series.getEndIndex();

        if (last < minBarsForSignal) {
            log.debug("[{}][{}] Only {} bars — waiting for {}", symbol, timeframe, last + 1, minBarsForSignal);
            return insufficientData(candle, windowStart, windowEnd);
        }

        double close = candle.close;
        double eFast = emaFast.getValue(last).doubleValue();
        double eSlow = emaSlow.getValue(last).doubleValue();
        double rsiVal = rsi.getValue(last).doubleValue();
        double atrVal = atr.getValue(last).doubleValue();
        double relVol = computeRelVolume(candle.volume);

        // MACD values for the current and previous bar (to detect crossovers)
        double macdValue = macd.getValue(last).doubleValue();
        double macdSig = macdSignal.getValue(last).doubleValue();
        double prevMacd = macd.getValue(last - 1).doubleValue();
        double prevMacdSig = macdSignal.getValue(last - 1).doubleValue();

        // ── Trend classification ──────────────────────────────────────────────
        boolean bullish = close > eFast && eFast > eSlow;
        boolean bearish = close < eFast && eFast < eSlow;
        boolean sideways = !bullish && !bearish;

        // MACD Signals
        boolean macdBullishCrossover = prevMacd <= prevMacdSig && macdValue > macdSig;
        boolean macdBearishCrossover = prevMacd >= prevMacdSig && macdValue < macdSig;

        // ── Strategy selection (highest-priority wins) ────────────────────────
        Signal signal = Signal.HOLD;
        double confidence = 0.0;
        String source = "NONE";

        // 1. MACD Strategy (Confidence 0.85) - "MACD Crossover"
        // MACD crosses signal line in the direction of the trend
        if (bullish && macdBullishCrossover && macdValue < 0) { // best if crossover happens below zero line
            signal = Signal.BUY;
            confidence = 0.85;
            source = "MACD_CROSSOVER";
        } else if (bearish && macdBearishCrossover && macdValue > 0) { // best if crossover happens above zero line
            signal = Signal.SELL;
            confidence = 0.85;
            source = "MACD_CROSSOVER";
        }

        // 2. Mean Reversion (confidence 0.70) — only applied when sideways
        if (signal == Signal.HOLD && sideways && rsiVal < 30) {
            signal = Signal.BUY;
            confidence = 0.70;
            source = "MEAN_REVERSION";
        } else if (signal == Signal.HOLD && sideways && rsiVal > 70) {
            signal = Signal.SELL;
            confidence = 0.70;
            source = "MEAN_REVERSION";
        }

        // 3. Volatility Breakout (confidence 0.90) — overrides when relVol exceeds threshold
        if (relVol > relVolThreshold && atrVal > 0) {
            Signal breakout = bullish ? Signal.BUY : (bearish ? Signal.SELL : Signal.HOLD);
            if (breakout != Signal.HOLD) {
                signal = breakout;
                confidence = 0.90;
                source = "VOLATILITY_BREAKOUT";
            }
        }

        // 4. Price Action (confidence 0.88) — Overrides basic trend following if specific triggers match
        Bar prevBar = series.getBar(last - 1);
        boolean bullishPin = isBullishPinBar(candle.open, candle.high, candle.low, candle.close);
        boolean bearishPin = isBearishPinBar(candle.open, candle.high, candle.low, candle.close);
        boolean bullEngulf = isBullishEngulfing(prevBar, candle);
        boolean bearEngulf = isBearishEngulfing(prevBar, candle);

        // Area of Value: Price is pulling back near the fast EMA
        boolean nearEmaFast = Math.abs(close - eFast) / close < 0.005;

        if (bullish && nearEmaFast && (bullishPin || bullEngulf)) {
            signal = Signal.BUY;
            confidence = 0.88;
            source = "PRICE_ACTION";
        } else if (bearish && nearEmaFast && (bearishPin || bearEngulf)) {
            signal = Signal.SELL;
            confidence = 0.88;
            source = "PRICE_ACTION";
        }

        log.debug("[{}][{}] close={} emaFast={} emaSlow={} rsi={} macd={} macdSig={} relVol={} → {} conf={}",
                symbol, timeframe,
                String.format("%.2f", close),
                String.format("%.2f", eFast),
                String.format("%.2f", eSlow),
                String.format("%.1f", rsiVal),
                String.format("%.4f", macdValue),
                String.format("%.4f", macdSig),
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
                .ema20(eFast).ema50(eSlow).rsi14(rsiVal).atr14(atrVal).relVolume(relVol)
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
        int count = Math.min(volumeCount, relVolPeriod);
        if (count < 5) return 1.0;
        double sum = 0;
        for (int i = 0; i < count; i++) sum += volumeWindow[i];
        double avg = sum / count;
        return avg > 0 ? (double) currentVolume / avg : 1.0;
    }

    // ── Price Action Detection Helpers ────────────────────────────────────────

    private boolean isBullishPinBar(double open, double high, double low, double close) {
        double bodySize = Math.abs(close - open);
        double lowerWick = Math.min(open, close) - low;
        double upperWick = high - Math.max(open, close);
        return (lowerWick >= bodySize * 2) && (upperWick <= bodySize);
    }

    private boolean isBearishPinBar(double open, double high, double low, double close) {
        double bodySize = Math.abs(close - open);
        double lowerWick = Math.min(open, close) - low;
        double upperWick = high - Math.max(open, close);
        return (upperWick >= bodySize * 2) && (lowerWick <= bodySize);
    }

    private boolean isBullishEngulfing(Bar prev, Candle curr) {
        double prevOpen = prev.getOpenPrice().doubleValue();
        double prevClose = prev.getClosePrice().doubleValue();
        boolean prevBearish = prevClose < prevOpen;
        boolean currBullish = curr.close > curr.open;
        return prevBearish && currBullish
                && curr.close > prevOpen && curr.open < prevClose;
    }

    private boolean isBearishEngulfing(Bar prev, Candle curr) {
        double prevOpen = prev.getOpenPrice().doubleValue();
        double prevClose = prev.getClosePrice().doubleValue();
        boolean prevBullish = prevClose > prevOpen;
        boolean currBearish = curr.close < curr.open;
        return prevBullish && currBearish
                && curr.close < prevOpen && curr.open > prevClose;
    }

    // ── Accessors for testing ──────────────────────────────────────────────────

    int getEmaFastPeriod() { return emaFastPeriod; }
    int getEmaSlowPeriod() { return emaSlowPeriod; }
    int getRsiPeriod() { return rsiPeriod; }
    int getAtrPeriod() { return atrPeriod; }
    int getRelVolPeriod() { return relVolPeriod; }
    int getMinBarsForSignal() { return minBarsForSignal; }
    double getRelVolThreshold() { return relVolThreshold; }
    int getBarCount() { return series.getBarCount(); }
}
