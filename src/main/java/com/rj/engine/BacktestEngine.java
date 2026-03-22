package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.Candle;
import com.rj.model.CandleRecommendation;
import com.rj.model.ExecutionMode;
import com.rj.model.OpenPosition;
import com.rj.model.OrderFill;
import com.rj.model.Signal;
import com.rj.model.Timeframe;
import com.rj.model.TradeRecord;
import com.rj.model.TradeSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Synchronous backtesting engine.
 *
 * <p>Replays a list of M5 candles through the exact same analysis and strategy
 * logic used in live/paper modes — same {@link CandleAnalyzer}, same compound
 * rules. Multi-timeframe (M15, H1) candles are derived by aggregating M5 bars.</p>
 *
 * <h3>Fill model</h3>
 * Entry fills at next candle's open + slippage.
 * SL/TP fills at the trigger price (intra-candle hit detected against candle high/low).
 * If both SL and TP are hit within the same candle, SL takes priority (conservative).
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * List<Candle> m5history = ...;
 * BacktestEngine engine = new BacktestEngine(m5history, "NSE:SBIN-EQ", riskConfig);
 * StrategyReport report = engine.run();
 * System.out.println(report.summary());
 * }</pre>
 */
public class BacktestEngine {

    private static final Logger log = LoggerFactory.getLogger(BacktestEngine.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final List<Candle>           m5Candles;
    private final String                 symbol;
    private final RiskConfig             riskConfig;
    private final BacktestOrderExecutor  executor;
    private final TradeJournal           journal;

    // Per-timeframe analyzers (each owns its own ta4j BarSeries)
    private final CandleAnalyzer m5Analyzer;
    private final CandleAnalyzer m15Analyzer;
    private final CandleAnalyzer h1Analyzer;

    // Aggregation buffers
    private final List<Candle> m15Buffer = new ArrayList<>(3);
    private final List<Candle> h1Buffer  = new ArrayList<>(12);

    // Latest recommendation per timeframe
    private final Map<Timeframe, CandleRecommendation> latestRecs = new EnumMap<>(Timeframe.class);

    // Active position (at most one per symbol in backtest)
    private OpenPosition   openPosition;
    private TradeRecord    openRecord;
    private int            consecutiveLosses = 0;

    // Cooldown tracking
    private Instant lastExitTime = Instant.EPOCH;

    public BacktestEngine(List<Candle> m5Candles, String symbol, RiskConfig riskConfig) {
        this(m5Candles, symbol, riskConfig,
             new BacktestOrderExecutor(),
             new TradeJournal(ExecutionMode.BACKTEST));
    }

    BacktestEngine(List<Candle> m5Candles, String symbol, RiskConfig riskConfig,
                   BacktestOrderExecutor executor, TradeJournal journal) {
        this.m5Candles  = new ArrayList<>(m5Candles);
        this.symbol     = symbol;
        this.riskConfig = riskConfig;
        this.executor   = executor;
        this.journal    = journal;
        this.m5Analyzer  = new CandleAnalyzer(symbol, Timeframe.M5);
        this.m15Analyzer = new CandleAnalyzer(symbol, Timeframe.M15);
        this.h1Analyzer  = new CandleAnalyzer(symbol, Timeframe.H1);
    }

    // ── Run ───────────────────────────────────────────────────────────────────

    /**
     * Replays all candles and returns the complete strategy performance report.
     */
    public StrategyAnalyzer.Report run() {
        log.info("[BT][{}] Starting backtest on {} M5 candles", symbol, m5Candles.size());

        for (int i = 0; i < m5Candles.size(); i++) {
            Candle m5    = m5Candles.get(i);
            Candle nextM5 = i + 1 < m5Candles.size() ? m5Candles.get(i + 1) : null;

            // ── Update open position against current candle ───────────────────
            if (openPosition != null) {
                checkPositionAgainstCandle(m5);
            }

            // ── Feed M5 candle to analyzer ────────────────────────────────────
            Instant winStart = Instant.ofEpochSecond(m5.timestamp);
            Instant winEnd   = winStart.plus(Timeframe.M5.getDuration());
            CandleRecommendation m5Rec = m5Analyzer.addAndAnalyze(m5, winStart, winEnd);
            latestRecs.put(Timeframe.M5, m5Rec);

            // ── Aggregate to M15 (every 3 M5 candles) ────────────────────────
            m15Buffer.add(m5);
            if (m15Buffer.size() == 3) {
                Candle m15     = aggregate(m15Buffer, Timeframe.M15);
                Instant m15End = Instant.ofEpochSecond(m15.timestamp).plus(Timeframe.M15.getDuration());
                CandleRecommendation m15Rec = m15Analyzer.addAndAnalyze(
                        m15, Instant.ofEpochSecond(m15.timestamp), m15End);
                latestRecs.put(Timeframe.M15, m15Rec);
                m15Buffer.clear();
            }

            // ── Aggregate to H1 (every 12 M5 candles) ────────────────────────
            h1Buffer.add(m5);
            if (h1Buffer.size() == 12) {
                Candle h1     = aggregate(h1Buffer, Timeframe.H1);
                Instant h1End = Instant.ofEpochSecond(h1.timestamp).plus(Timeframe.H1.getDuration());
                CandleRecommendation h1Rec = h1Analyzer.addAndAnalyze(
                        h1, Instant.ofEpochSecond(h1.timestamp), h1End);
                latestRecs.put(Timeframe.H1, h1Rec);
                h1Buffer.clear();
            }

            // ── Try entry on next candle if no open position ──────────────────
            if (openPosition == null && nextM5 != null) {
                evaluateEntry(m5, nextM5);
            }
        }

        // ── Force-close any position that is still open at end of data ────────
        if (openPosition != null && !m5Candles.isEmpty()) {
            Candle last = m5Candles.get(m5Candles.size() - 1);
            forceClose(last.close, Instant.ofEpochSecond(last.timestamp),
                       PositionMonitor.ExitReason.FORCE_SQUAREOFF);
        }

        List<TradeRecord> trades = journal.closedTrades();
        log.info("[BT][{}] Backtest complete: {} trades", symbol, trades.size());
        return StrategyAnalyzer.analyze(trades);
    }

    // ── Entry evaluation ──────────────────────────────────────────────────────

    private void evaluateEntry(Candle currentCandle, Candle nextCandle) {
        // Time filter
        ZonedDateTime candleTime = ZonedDateTime.ofInstant(
                Instant.ofEpochSecond(currentCandle.timestamp), IST);
        if (candleTime.toLocalTime().isAfter(riskConfig.getNoNewTradesAfter())) return;

        // Consecutive loss kill switch
        if (consecutiveLosses >= riskConfig.getMaxConsecutiveLossesPerStrategy()) {
            log.debug("[BT] Kill switch: {} consecutive losses", consecutiveLosses);
            return;
        }

        // Cooldown
        if (Instant.ofEpochSecond(currentCandle.timestamp)
                .isBefore(lastExitTime.plus(java.time.Duration.ofMinutes(25)))) {
            return;
        }

        // Compound signal
        Optional<TradeSignal> signal = compoundSignal();
        if (signal.isEmpty()) return;

        TradeSignal sig      = signal.get();
        double      entry    = nextCandle.open;
        double      atr      = latestRecs.get(Timeframe.M5).getAtr14();
        double      riskUnit = atr > 0 ? 2 * atr : entry * 0.01;
        double      sl       = sig.getDirection() == Signal.BUY ? entry - riskUnit : entry + riskUnit;
        double      tp       = sig.getDirection() == Signal.BUY ? entry + (2 * riskUnit) : entry - (2 * riskUnit);

        // Position sizing: 2% risk per trade
        double riskBudget  = riskConfig.getInitialCapitalInr() * riskConfig.getMaxRiskPerTradePercent();
        int    quantity    = (int) Math.floor(riskBudget / riskUnit);
        quantity = Math.min(quantity, riskConfig.getMaxQuantityPerOrder());
        if (quantity <= 0) {
            log.debug("[BT] Quantity=0, skipping trade");
            return;
        }

        executor.setNextBar(nextCandle.open, Instant.ofEpochSecond(nextCandle.timestamp));
        OrderFill fill = executor.placeEntry(sig, quantity);

        if (!fill.isSuccess()) {
            journal.logSignalRejected(sig, fill.getRejectReason());
            return;
        }

        journal.logSignalGenerated(sig);
        journal.logOrderEntry(sig, fill);

        openPosition = new OpenPosition(
                symbol, sig.getCorrelationId(), sig.getStrategyId(),
                sig.getDirection(), fill.getFillPrice(), fill.getFillQuantity(),
                sl, tp, fill.getFillTime());

        openRecord = new TradeRecord(
                sig.getCorrelationId(), symbol, sig.getStrategyId(),
                ExecutionMode.BACKTEST, sig.getDirection(),
                fill.getFillPrice(), fill.getFillQuantity(), sl, tp,
                fill.getFillTime(), atr, sig.getConfidence(), sig.getTimeframeVotes());

        log.info("[BT][{}] Opened: {} @ {:.2f} sl={:.2f} tp={:.2f} qty={}",
                symbol, sig.getDirection(), fill.getFillPrice(), sl, tp, quantity);
    }

    // ── Position check against a candle ──────────────────────────────────────

    private void checkPositionAgainstCandle(Candle candle) {
        if (openPosition == null) return;

        // Update MAE/MFE
        openRecord.updateExcursion(candle.low);
        openRecord.updateExcursion(candle.high);
        openRecord.updateExcursion(candle.close);

        boolean slHit = openPosition.isStopLossHit(candle.low)   // for longs
                     || openPosition.isStopLossHit(candle.high);  // for shorts
        boolean tpHit = openPosition.isTakeProfitHit(candle.high) // for longs
                     || openPosition.isTakeProfitHit(candle.low); // for shorts

        // If both hit in same candle, SL takes priority (conservative)
        if (slHit) {
            closePosition(openPosition.getCurrentStopLoss(),
                          Instant.ofEpochSecond(candle.timestamp),
                          PositionMonitor.ExitReason.STOP_LOSS);
        } else if (tpHit) {
            closePosition(openPosition.getTakeProfit(),
                          Instant.ofEpochSecond(candle.timestamp),
                          PositionMonitor.ExitReason.TAKE_PROFIT);
        } else {
            // Update trailing stop
            updateTrailingStop(candle.high, candle.low);

            // Time-based exit at 15:15
            ZonedDateTime t = ZonedDateTime.ofInstant(
                    Instant.ofEpochSecond(candle.timestamp), IST);
            if (t.toLocalTime().compareTo(riskConfig.getMarketCloseTime()) >= 0) {
                forceClose(candle.close, Instant.ofEpochSecond(candle.timestamp),
                           PositionMonitor.ExitReason.FORCE_SQUAREOFF);
            }
        }
    }

    private void updateTrailingStop(double high, double low) {
        double price = openPosition.getDirection() == Signal.BUY ? high : low;
        double pnlPct = (price - openPosition.getEntryPrice()) / openPosition.getEntryPrice();
        if (openPosition.getDirection() == Signal.SELL) pnlPct = -pnlPct;

        if (!openPosition.isTrailingActivated()
                && pnlPct >= riskConfig.getTrailingActivationPercent()) {
            openPosition.setTrailingActivated(true);
        }

        if (!openPosition.isTrailingActivated()) return;
        openPosition.updateHighWaterMark(price);
        double hwm     = openPosition.getHighWaterMark();
        double newStop = openPosition.getDirection() == Signal.BUY
                ? hwm * (1 - riskConfig.getTrailingStepPercent())
                : hwm * (1 + riskConfig.getTrailingStepPercent());
        openPosition.stepTrailingStop(newStop);
    }

    private void closePosition(double exitPx, Instant exitTime,
                               PositionMonitor.ExitReason reason) {
        executor.setNextBar(exitPx, exitTime);
        OrderFill fill = executor.placeExit(openPosition, reason, exitPx);
        double actualExit = fill.isSuccess() ? fill.getFillPrice() : exitPx;

        openRecord.close(actualExit, exitTime, reason);
        journal.logOrderExit(openPosition, fill, reason);
        journal.logTradeClosed(openRecord);

        boolean winner = openRecord.isWinner();
        consecutiveLosses = winner ? 0 : consecutiveLosses + 1;
        lastExitTime      = exitTime;

        log.info("[BT][{}] Closed: {} @ {:.2f} pnl={:.2f} reason={}",
                symbol, openPosition.getDirection(),
                actualExit, openRecord.getPnl(), reason);

        openPosition = null;
        openRecord   = null;
    }

    private void forceClose(double exitPx, Instant exitTime,
                            PositionMonitor.ExitReason reason) {
        closePosition(exitPx, exitTime, reason);
    }

    // ── Compound signal (same logic as StrategyEvaluator) ────────────────────

    private Optional<TradeSignal> compoundSignal() {
        CandleRecommendation m5  = latestRecs.get(Timeframe.M5);
        CandleRecommendation m15 = latestRecs.get(Timeframe.M15);
        CandleRecommendation h1  = latestRecs.get(Timeframe.H1);

        if (m5 == null || m15 == null) return Optional.empty();
        if (!m5.getSignal().isDirectional()) return Optional.empty();
        if (m5.getSignal() != m15.getSignal()) return Optional.empty();

        Signal h1Signal = h1 != null ? h1.getSignal() : Signal.HOLD;
        if (h1Signal.isDirectional() && h1Signal != m5.getSignal()) return Optional.empty();

        double base       = (m5.getConfidence() + m15.getConfidence()) / 2.0;
        double boost      = (h1 != null && h1Signal == m5.getSignal()) ? 0.05 : 0.0;
        double confidence = Math.min(1.0, base + boost);
        if (confidence < 0.70) return Optional.empty();

        double entry   = m5.getCandle().close;
        double atr     = m5.getAtr14() > 0 ? m5.getAtr14() : entry * 0.01;
        double sl      = m5.getSignal() == Signal.BUY ? entry - 2*atr : entry + 2*atr;
        double tp      = m5.getSignal() == Signal.BUY ? entry + 4*atr : entry - 4*atr;
        String corrId  = symbol + "_" + m5.getSignal() + "_" + m5.getWindowStart().getEpochSecond();

        return Optional.of(TradeSignal.builder()
                .symbol(symbol)
                .correlationId(corrId)
                .direction(m5.getSignal())
                .confidence(confidence)
                .suggestedEntry(entry)
                .suggestedStopLoss(sl)
                .suggestedTarget(tp)
                .strategyId(m5.getStrategySource())
                .vote(Timeframe.M5, m5.getSignal())
                .vote(Timeframe.M15, m15.getSignal())
                .vote(Timeframe.H1, h1Signal)
                .build());
    }

    // ── Candle aggregation ────────────────────────────────────────────────────

    /** Aggregates a list of lower-timeframe candles into one higher-TF candle. */
    private static Candle aggregate(List<Candle> bars, Timeframe tf) {
        long   ts     = bars.get(0).timestamp;
        double open   = bars.get(0).open;
        double close  = bars.get(bars.size() - 1).close;
        double high   = bars.stream().mapToDouble(c -> c.high).max().orElse(open);
        double low    = bars.stream().mapToDouble(c -> c.low).min().orElse(open);
        long   volume = bars.stream().mapToLong(c -> c.volume).sum();
        return Candle.of(ts, open, high, low, close, volume);
    }
}
