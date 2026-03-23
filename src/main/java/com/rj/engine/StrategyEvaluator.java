package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.CandleRecommendation;
import com.rj.model.Signal;
import com.rj.model.Timeframe;
import com.rj.model.TradeSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Thread 3 — Strategy Evaluator.
 *
 * <p>Single virtual thread that consumes {@link CandleRecommendation}s from the
 * candle pipeline.  For each symbol it maintains the latest recommendation per
 * timeframe and applies a compound multi-timeframe strategy:</p>
 *
 * <ol>
 *   <li>M5 must emit a directional signal (BUY or SELL).</li>
 *   <li>M15 must agree with M5.</li>
 *   <li>H1 must not actively disagree (HOLD is acceptable).</li>
 *   <li>Combined confidence must exceed {@value #MIN_CONFIDENCE}.</li>
 *   <li>Symbol must not be in post-exit cooldown.</li>
 *   <li>Symbol must not have an active open position.</li>
 *   <li>Time must be before the no-new-trades cutoff.</li>
 * </ol>
 *
 * <p>When all conditions pass a {@link TradeSignal} is published to
 * the provided {@code signalConsumer} (typically the risk + OMS layer).</p>
 */
public class StrategyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(StrategyEvaluator.class);

    private static final double MIN_CONFIDENCE = 0.70;
    private static final Duration COOLDOWN_DURATION = Duration.ofMinutes(25); // 5 candles × 5 min

    private final BlockingQueue<CandleRecommendation> inQueue;
    private final Consumer<TradeSignal> signalConsumer;
    private final RiskConfig riskConfig;
    private final PositionMonitor positionMonitor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    // Latest recommendation per (symbol → (timeframe → recommendation))
    private final ConcurrentHashMap<String, Map<Timeframe, CandleRecommendation>> latestRecs
            = new ConcurrentHashMap<>();
    // Cooldown: symbol → time of last exit (set by PositionMonitor via onPositionClosed)
    private final ConcurrentHashMap<String, Instant> lastExitTime = new ConcurrentHashMap<>();
    private volatile Thread thread;

    public StrategyEvaluator(BlockingQueue<CandleRecommendation> inQueue,
                             Consumer<TradeSignal> signalConsumer,
                             RiskConfig riskConfig,
                             PositionMonitor positionMonitor) {
        this.inQueue = inQueue;
        this.signalConsumer = signalConsumer;
        this.riskConfig = riskConfig;
        this.positionMonitor = positionMonitor;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("StrategyEvaluator already running");
            return;
        }
        thread = Thread.ofVirtual()
                .name("strategy-evaluator")
                .start(this::evaluationLoop);
        log.info("StrategyEvaluator started");
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (thread != null) thread.interrupt();
        log.info("StrategyEvaluator stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    // ── Called by PositionMonitor when a position is closed ───────────────────

    public void onPositionClosed(String symbol) {
        lastExitTime.put(symbol, Instant.now());
        log.info("[{}] Cooldown started — no new entries for {}", symbol, COOLDOWN_DURATION);
    }

    // ── Main evaluation loop ──────────────────────────────────────────────────

    private void evaluationLoop() {
        log.info("StrategyEvaluator evaluation loop running");
        while (!Thread.currentThread().isInterrupted() && running.get()) {
            try {
                CandleRecommendation rec = inQueue.poll(1, TimeUnit.SECONDS);
                if (rec == null) continue;

                updateLatest(rec);
                evaluateSymbol(rec.getSymbol());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Unexpected error in strategy evaluation loop: {}", e.getMessage(), e);
            }
        }
        log.info("StrategyEvaluator evaluation loop stopped");
    }

    private void updateLatest(CandleRecommendation rec) {
        latestRecs
                .computeIfAbsent(rec.getSymbol(), k -> new EnumMap<>(Timeframe.class))
                .put(rec.getTimeframe(), rec);
    }

    private void evaluateSymbol(String symbol) {
        Map<Timeframe, CandleRecommendation> votes = latestRecs.get(symbol);
        if (votes == null) return;

        Optional<TradeSignal> signal = buildCompoundSignal(symbol, votes);
        signal.ifPresentOrElse(
                sig -> {
                    log.info("[{}] Compound signal generated: {}", symbol, sig);
                    signalConsumer.accept(sig);
                },
                () -> log.debug("[{}] No compound signal this cycle", symbol));
    }

    // ── Compound strategy logic ───────────────────────────────────────────────

    private Optional<TradeSignal> buildCompoundSignal(
            String symbol, Map<Timeframe, CandleRecommendation> votes) {

        CandleRecommendation m5 = votes.get(Timeframe.M5);
        CandleRecommendation m15 = votes.get(Timeframe.M15);
        CandleRecommendation h1 = votes.get(Timeframe.H1);

        // ── Gate 1: require M5 and M15 ───────────────────────────────────────
        if (m5 == null || m15 == null) {
            log.debug("[{}] Missing M5/M15 — skipping", symbol);
            return Optional.empty();
        }

        // ── Gate 2: M5 must be directional ───────────────────────────────────
        Signal m5Signal = m5.getSignal();
        if (!m5Signal.isDirectional()) return Optional.empty();

        // ── Gate 3: M15 must agree with M5 ───────────────────────────────────
        if (m15.getSignal() != m5Signal) {
            log.debug("[{}] M5({}) M15({}) disagree — rejecting", symbol, m5Signal, m15.getSignal());
            return Optional.empty();
        }

        // ── Gate 4: H1 must not actively oppose ──────────────────────────────
        Signal h1Signal = h1 != null ? h1.getSignal() : Signal.HOLD;
        if (h1Signal.isDirectional() && h1Signal != m5Signal) {
            log.info("[{}] H1 {} opposes M5/M15 {} — rejecting", symbol, h1Signal, m5Signal);
            return Optional.empty();
        }

        // ── Gate 5: confidence threshold ─────────────────────────────────────
        double confidence = combineConfidence(m5, m15, h1, m5Signal);
        if (confidence < MIN_CONFIDENCE) {
            log.debug("[{}] Combined confidence {:.2f} below threshold {}", symbol, confidence, MIN_CONFIDENCE);
            return Optional.empty();
        }

        // ── Gate 6: time filter ───────────────────────────────────────────────
        ZonedDateTime now = ZonedDateTime.now(riskConfig.getExchangeZone());
        if (now.toLocalTime().isAfter(riskConfig.getNoNewTradesAfter())) {
            log.debug("[{}] Past no-new-trades cutoff {} IST", symbol, riskConfig.getNoNewTradesAfter());
            return Optional.empty();
        }

        // ── Gate 7: cooldown ─────────────────────────────────────────────────
        Instant lastExit = lastExitTime.get(symbol);
        if (lastExit != null && Duration.between(lastExit, Instant.now()).compareTo(COOLDOWN_DURATION) < 0) {
            log.debug("[{}] In cooldown since {}", symbol, lastExit);
            return Optional.empty();
        }

        // ── Gate 8: no open position ──────────────────────────────────────────
        if (positionMonitor.hasOpenPosition(symbol)) {
            log.debug("[{}] Open position exists — skipping entry", symbol);
            return Optional.empty();
        }

        // ── Build trade signal ────────────────────────────────────────────────
        double entry = m5.getCandle().close;
        double atr = m5.getAtr14() > 0 ? m5.getAtr14() : entry * 0.01; // fallback 1%
        double sl = m5Signal == Signal.BUY ? entry - (2 * atr) : entry + (2 * atr);
        double tp = m5Signal == Signal.BUY ? entry + (4 * atr) : entry - (4 * atr); // 2R

        String correlationId = symbol + "_" + m5Signal + "_" + m5.getWindowStart().getEpochSecond();

        TradeSignal sig = TradeSignal.builder()
                .symbol(symbol)
                .correlationId(correlationId)
                .direction(m5Signal)
                .confidence(confidence)
                .suggestedEntry(entry)
                .suggestedStopLoss(sl)
                .suggestedTarget(tp)
                .strategyId(m5.getStrategySource())
                .vote(Timeframe.M5, m5Signal)
                .vote(Timeframe.M15, m15.getSignal())
                .vote(Timeframe.H1, h1Signal)
                .build();

        log.info("[{}] {} confidence={} R={} correlationId={}",
                symbol, sig.getDirection(),
                String.format("%.2f", sig.getConfidence()),
                String.format("%.1f", sig.rMultiple()),
                correlationId);

        return Optional.of(sig);
    }

    /**
     * Weighted average confidence:
     * H1 agreement gives a +5 % boost; absence of H1 is neutral.
     */
    private double combineConfidence(CandleRecommendation m5,
                                     CandleRecommendation m15,
                                     CandleRecommendation h1,
                                     Signal direction) {
        double base = (m5.getConfidence() + m15.getConfidence()) / 2.0;
        double boost = (h1 != null && h1.getSignal() == direction) ? 0.05 : 0.0;
        return Math.min(1.0, base + boost);
    }

    public int queueDepth() {
        return inQueue.size();
    }
}
