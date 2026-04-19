package com.rj.engine;

import com.rj.model.*;
import com.rj.strategy.ITradeStrategy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Feeds M1 candles through per-timeframe analyzers and a pluggable
 * {@link ITradeStrategy}, emitting one event per bar the strategy fires on.
 *
 * <p>No position simulation — signal detection only. For full PnL backtests,
 * use {@link BacktestEngine}.
 */
public class SignalScanner {

    public record SignalEvent(TradeSignal signal, ZonedDateTime barTimestamp, LocalDate tradingDate) {}

    private final String symbol;
    private final Timeframe primary;
    private final ITradeStrategy strategy;
    private final CandleAnalyzer primaryAnalyzer;
    private final CandleAnalyzer m15Analyzer;
    private final CandleAnalyzer h1Analyzer;

    public SignalScanner(String symbol, Timeframe primary, ITradeStrategy strategy) {
        this.symbol = symbol;
        this.primary = primary;
        this.strategy = strategy;
        this.primaryAnalyzer = new CandleAnalyzer(symbol, primary);
        this.m15Analyzer = (primary == Timeframe.M15) ? null : new CandleAnalyzer(symbol, Timeframe.M15);
        this.h1Analyzer = (primary == Timeframe.H1) ? null : new CandleAnalyzer(symbol, Timeframe.H1);
    }

    public List<SignalEvent> scan(List<Candle> m1, LocalDate tradingDate) {
        if (m1 == null || m1.isEmpty()) return List.of();

        List<Candle> primaryBars = CandleAggregator.to(primary, m1);
        List<Candle> m15Bars = (m15Analyzer == null) ? List.of() : CandleAggregator.to(Timeframe.M15, m1);
        List<Candle> h1Bars  = (h1Analyzer  == null) ? List.of() : CandleAggregator.to(Timeframe.H1, m1);

        Map<Timeframe, CandleRecommendation> latest = new EnumMap<>(Timeframe.class);
        List<SignalEvent> out = new ArrayList<>();

        int m15Idx = 0, h1Idx = 0;
        for (Candle bar : primaryBars) {
            // Advance higher-tf indexes through any bars that closed up to or at `bar`.
            if (m15Analyzer != null) {
                while (m15Idx < m15Bars.size() && !m15Bars.get(m15Idx).timestamp.isAfter(bar.timestamp)) {
                    latest.put(Timeframe.M15, feed(m15Analyzer, m15Bars.get(m15Idx), Timeframe.M15));
                    m15Idx++;
                }
            }
            if (h1Analyzer != null) {
                while (h1Idx < h1Bars.size() && !h1Bars.get(h1Idx).timestamp.isAfter(bar.timestamp)) {
                    latest.put(Timeframe.H1, feed(h1Analyzer, h1Bars.get(h1Idx), Timeframe.H1));
                    h1Idx++;
                }
            }
            latest.put(primary, feed(primaryAnalyzer, bar, primary));

            strategy.evaluate(symbol, latest).ifPresent(sig ->
                out.add(new SignalEvent(sig, bar.timestamp, tradingDate)));
        }
        return out;
    }

    private static CandleRecommendation feed(CandleAnalyzer analyzer, Candle bar, Timeframe tf) {
        Instant start = bar.timestamp.toInstant();
        Instant end = start.plus(tf.getDuration());
        return analyzer.addAndAnalyze(bar, start, end, InstrumentInfo.EQUITY_DEFAULT);
    }
}
