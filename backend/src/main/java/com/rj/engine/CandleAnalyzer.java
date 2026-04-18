package com.rj.engine;

import com.rj.config.StrategyYamlConfig;
import com.rj.model.*;
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
 */
class CandleAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CandleAnalyzer.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final String symbol;
    private final Timeframe timeframe;
    private final BarSeries series;

    private final int emaFastPeriod;
    private final int emaSlowPeriod;
    private final int rsiPeriod;
    private final int atrPeriod;
    private final int relVolPeriod;
    private final int minBarsForSignal;
    private final double relVolThreshold;

    private final EMAIndicator emaFast;
    private final EMAIndicator emaSlow;
    private final RSIIndicator rsi;
    private final ATRIndicator atr;
    private final MACDIndicator macd;
    private final EMAIndicator macdSignal;

    private final double[] volumeWindow;
    private int volumeCount = 0;

    CandleAnalyzer(String symbol, Timeframe timeframe) {
        this(symbol, timeframe, new StrategyYamlConfig.Indicators(), new StrategyYamlConfig.Entry());
    }

    CandleAnalyzer(String symbol, Timeframe timeframe,
                   StrategyYamlConfig.Indicators indicators,
                   StrategyYamlConfig.Entry entry) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.series = new BaseBarSeries(symbol + "_" + timeframe.getLabel(), DoubleNum::valueOf);
        this.series.setMaximumBarCount(200);

        this.emaFastPeriod = indicators.getEmaFast();
        this.emaSlowPeriod = indicators.getEmaSlow();
        this.rsiPeriod = indicators.getRsiPeriod();
        this.atrPeriod = indicators.getAtrPeriod();
        this.relVolPeriod = indicators.getRelVolPeriod();
        this.minBarsForSignal = indicators.getMinCandles();
        this.relVolThreshold = entry.getRelVolThreshold();

        this.volumeWindow = new double[this.relVolPeriod];

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        this.emaFast = new EMAIndicator(closePrice, emaFastPeriod);
        this.emaSlow = new EMAIndicator(closePrice, emaSlowPeriod);
        this.rsi = new RSIIndicator(closePrice, rsiPeriod);
        this.atr = new ATRIndicator(series, atrPeriod);
        this.macd = new MACDIndicator(closePrice, 12, 26);
        this.macdSignal = new EMAIndicator(macd, 9);
    }

    CandleRecommendation addAndAnalyze(Candle candle, Instant windowStart, Instant windowEnd, InstrumentInfo info) {
        appendBar(candle, windowEnd);
        return analyze(candle, windowStart, windowEnd, info);
    }

    private void appendBar(Candle candle, Instant windowEnd) {
        ZonedDateTime endTime = ZonedDateTime.ofInstant(windowEnd, IST);
        try {
            Bar bar = new BaseBar(timeframe.getDuration(), endTime,
                    DoubleNum.valueOf(candle.open), DoubleNum.valueOf(candle.high),
                    DoubleNum.valueOf(candle.low), DoubleNum.valueOf(candle.close),
                    DoubleNum.valueOf(candle.volume), DoubleNum.valueOf(0));
            series.addBar(bar);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to add bar: {}", symbol, timeframe, e.getMessage());
        }
        volumeWindow[volumeCount % relVolPeriod] = candle.volume;
        volumeCount++;
    }

    private CandleRecommendation analyze(Candle candle, Instant windowStart, Instant windowEnd, InstrumentInfo info) {
        int last = series.getEndIndex();
        if (last < minBarsForSignal) {
            return insufficientData(candle, windowStart, windowEnd, info);
        }

        double close = candle.close;
        double eFast = emaFast.getValue(last).doubleValue();
        double eSlow = emaSlow.getValue(last).doubleValue();
        double rsiVal = rsi.getValue(last).doubleValue();
        double atrVal = atr.getValue(last).doubleValue();
        double relVol = computeRelVolume(candle.volume);

        double macdValue = macd.getValue(last).doubleValue();
        double macdSig = macdSignal.getValue(last).doubleValue();
        double prevMacd = macd.getValue(last - 1).doubleValue();
        double prevMacdSig = macdSignal.getValue(last - 1).doubleValue();

        boolean bullish = close > eFast && eFast > eSlow;
        boolean bearish = close < eFast && eFast < eSlow;
        boolean sideways = !bullish && !bearish;

        boolean macdBullishCrossover = prevMacd <= prevMacdSig && macdValue > macdSig;
        boolean macdBearishCrossover = prevMacd >= prevMacdSig && macdValue < macdSig;

        Signal signal = Signal.HOLD;
        double confidence = 0.0;
        String source = "NONE";

        if (bullish && macdBullishCrossover && macdValue < 0) {
            signal = Signal.BUY; confidence = 0.85; source = "MACD_CROSSOVER";
        } else if (bearish && macdBearishCrossover && macdValue > 0) {
            signal = Signal.SELL; confidence = 0.85; source = "MACD_CROSSOVER";
        }

        if (signal == Signal.HOLD && sideways && rsiVal < 30) {
            signal = Signal.BUY; confidence = 0.70; source = "MEAN_REVERSION";
        } else if (signal == Signal.HOLD && sideways && rsiVal > 70) {
            signal = Signal.SELL; confidence = 0.70; source = "MEAN_REVERSION";
        }

        if (relVol > relVolThreshold && atrVal > 0) {
            Signal breakout = bullish ? Signal.BUY : (bearish ? Signal.SELL : Signal.HOLD);
            if (breakout != Signal.HOLD) {
                signal = breakout; confidence = 0.90; source = "VOLATILITY_BREAKOUT";
            }
        }

        return CandleRecommendation.builder()
                .symbol(symbol).timeframe(timeframe)
                .windowStart(windowStart).windowEnd(windowEnd)
                .signal(signal).confidence(confidence).strategySource(source)
                .ema20(eFast).ema50(eSlow).rsi14(rsiVal).atr14(atrVal).relVolume(relVol)
                .candle(candle).instrumentInfo(info).build();
    }

    private CandleRecommendation insufficientData(Candle candle, Instant ws, Instant we, InstrumentInfo info) {
        return CandleRecommendation.builder()
                .symbol(symbol).timeframe(timeframe).windowStart(ws).windowEnd(we)
                .signal(Signal.HOLD).confidence(0.0).strategySource("INSUFFICIENT_DATA")
                .candle(candle).instrumentInfo(info).build();
    }

    private double computeRelVolume(long currentVolume) {
        int count = Math.min(volumeCount, relVolPeriod);
        if (count < 5) return 1.0;
        double sum = 0;
        for (int i = 0; i < count; i++) sum += volumeWindow[i];
        double avg = sum / count;
        return avg > 0 ? (double) currentVolume / avg : 1.0;
    }

    public int getEmaFastPeriod() { return emaFastPeriod; }
    public int getEmaSlowPeriod() { return emaSlowPeriod; }
    public int getRsiPeriod() { return rsiPeriod; }
    public int getAtrPeriod() { return atrPeriod; }
    public int getRelVolPeriod() { return relVolPeriod; }
    public int getMinBarsForSignal() { return minBarsForSignal; }
    public double getRelVolThreshold() { return relVolThreshold; }
}
