package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyYamlConfig;
import com.rj.model.CandleRecommendation;
import com.rj.model.Signal;
import com.rj.model.Timeframe;
import com.rj.model.TradeSignal;
import com.rj.strategy.ITradeStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Thread 3 — Strategy Evaluator (Phase-II Pluggable).
 *
 * <p>Consumes {@link CandleRecommendation}s and delegates evaluation to
 * registered {@link ITradeStrategy} implementations.</p>
 */
public class StrategyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(StrategyEvaluator.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int DEFAULT_COOLDOWN_MINUTES = 25;

    private final BlockingQueue<CandleRecommendation> inQueue;
    private final Consumer<TradeSignal> signalConsumer;
    private final RiskConfig riskConfig;
    private final PositionMonitor positionMonitor;
    private final SignalJournal signalJournal;
    private final List<ITradeStrategy> strategies = new ArrayList<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Map<Timeframe, CandleRecommendation>> latestRecs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastExitTime = new ConcurrentHashMap<>();
    private volatile Thread thread;

    // Per-strategy YAML config (keyed by strategy name, e.g. "trend_following")
    private volatile Map<String, StrategyYamlConfig> strategyConfigs = Map.of();

    public StrategyEvaluator(BlockingQueue<CandleRecommendation> inQueue,
                             Consumer<TradeSignal> signalConsumer,
                             RiskConfig riskConfig,
                             PositionMonitor positionMonitor) {
        this.inQueue = inQueue;
        this.signalConsumer = signalConsumer;
        this.riskConfig = riskConfig;
        this.positionMonitor = positionMonitor;
        this.signalJournal = new SignalJournal();
    }

    /**
     * Updates the per-strategy YAML configs at runtime (called by ConfigFileWatcher on hot-reload).
     * Thread-safe: volatile reference swap.
     */
    public void updateStrategyConfigs(Map<String, StrategyYamlConfig> configs) {
        this.strategyConfigs = configs != null ? Map.copyOf(configs) : Map.of();
        log.info("StrategyEvaluator configs updated: {} strategies loaded", this.strategyConfigs.size());
    }

    /**
     * Returns the current per-strategy configs (for testing/introspection).
     */
    public Map<String, StrategyYamlConfig> getStrategyConfigs() {
        return strategyConfigs;
    }

    public void addStrategy(ITradeStrategy strategy) {
        this.strategies.add(strategy);
        log.info("Registered strategy: {} [{}]", strategy.getName(), strategy.getId());
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
        log.info("[{}] Cooldown started — no new entries for {} min", symbol, DEFAULT_COOLDOWN_MINUTES);
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

        // ── Phase-II: Iterate over registered strategies ────────────────────
        for (ITradeStrategy strategy : strategies) {
            strategy.evaluate(symbol, votes).ifPresent(sig -> {
                // Log all generated signals to the alpha journal (requirement)
                signalJournal.logSignal(sig);

                // Run global execution gates
                if (isExecutionAllowed(symbol, sig)) {
                    log.info("[{}] Strategy [{}] signal APPROVED for execution: {}",
                            symbol, strategy.getId(), sig);
                    signalConsumer.accept(sig);
                } else {
                    log.debug("[{}] Strategy [{}] signal rejected by global gates",
                            symbol, strategy.getId());
                }
            });
        }
    }

    private boolean isExecutionAllowed(String symbol, TradeSignal sig) {
        // ── Gate 1: time filter (global cutoff) ───────────────────────────
        ZonedDateTime now = ZonedDateTime.now(riskConfig.getExchangeZone());
        if (now.toLocalTime().isAfter(riskConfig.getNoNewTradesAfter())) {
            log.debug("[{}] Past no-new-trades cutoff {} IST", symbol, riskConfig.getNoNewTradesAfter());
            return false;
        }

        // ── Gate 2: cooldown ──────────────────────────────────────────────
        // In Phase-II we use a default or per-strategy cooldown logic
        // For now, using global default from Phase-I
        Duration cooldownDuration = Duration.ofMinutes(25);
        Instant lastExit = lastExitTime.get(symbol);
        if (lastExit != null && Duration.between(lastExit, Instant.now()).compareTo(cooldownDuration) < 0) {
            log.debug("[{}] In cooldown since {} (25min)", symbol, lastExit);
            return false;
        }

        // ── Gate 3: no open position ──────────────────────────────────────────
        if (positionMonitor.hasOpenPosition(symbol)) {
            log.debug("[{}] Open position exists — skipping entry", symbol);
            return false;
        }

        return true;
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

    // ── Strategy config resolution ─────────────────────────────────────────────

    /**
     * Resolves a per-strategy YAML config by matching the CandleAnalyzer strategy source
     * name (e.g. "MACD_CROSSOVER") against loaded YAML strategy keys (e.g. "trend_following").
     *
     * <p>Mapping convention: CandleAnalyzer emits upper-case source names;
     * YAML uses lower-case keys. The mapping is:</p>
     * <ul>
     *   <li>MACD_CROSSOVER, PRICE_ACTION → trend_following</li>
     *   <li>MEAN_REVERSION → mean_reversion</li>
     *   <li>VOLATILITY_BREAKOUT → volatility_breakout</li>
     * </ul>
     *
     * @return the matching config, or null if not found
     */
    private StrategyYamlConfig resolveStrategyConfig(String strategySource) {
        if (strategySource == null || strategyConfigs.isEmpty()) return null;

        // Direct match by lower-casing
        String lower = strategySource.toLowerCase();
        StrategyYamlConfig cfg = strategyConfigs.get(lower);
        if (cfg != null) return cfg;

        // Map CandleAnalyzer source names to YAML strategy keys
        return switch (strategySource) {
            case "MACD_CROSSOVER", "PRICE_ACTION" -> strategyConfigs.get("trend_following");
            case "MEAN_REVERSION" -> strategyConfigs.get("mean_reversion");
            case "VOLATILITY_BREAKOUT" -> strategyConfigs.get("volatility_breakout");
            default -> null;
        };
    }
}
