package com.rj.engine;

import com.rj.config.ConfigManager;
import com.rj.config.RiskConfig;
import com.rj.model.*;
import fyers.FyersPositions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main orchestrator — wires all five service threads and manages their lifecycle.
 *
 * <pre>
 * Thread 1  FyersSocketListener  → TickStore.append(tick)
 * Thread 2  CandleService        → BlockingQueue&lt;CandleRecommendation&gt;
 * Thread 3  StrategyEvaluator    → handleSignal() [risk → size → entry order → position]
 * Thread 4  PositionMonitor      → handleExit()   [exit order → journal → risk accounting]
 * Thread 5  HealthMonitor        → health log every 60 s
 * </pre>
 *
 * <h3>Execution mode selection</h3>
 * <ul>
 *   <li>{@code APP_ENV=backtest} — use {@link BacktestOrderExecutor}</li>
 *   <li>{@code APP_ENV=paper}    — use {@link PaperOrderExecutor} (default)</li>
 *   <li>{@code APP_ENV=live}     — use {@link LiveOrderExecutor}; go-live checklist must be complete</li>
 * </ul>
 */
public class TradingEngine {

    private static final Logger log = LoggerFactory.getLogger(TradingEngine.class);
    private static final int REC_QUEUE_CAPACITY = 2048;

    // ── Core dependencies ─────────────────────────────────────────────────────

    private final ExecutionMode mode;
    private final IOrderExecutor executor;
    private final RiskManager riskManager;
    private final TradeJournal journal;
    private final ConfigManager config;

    /**
     * Open trade records keyed by correlationId.
     * Created on entry fill; removed and closed on exit fill.
     */
    private final ConcurrentHashMap<String, TradeRecord> openRecords = new ConcurrentHashMap<>();

    // ── Services ──────────────────────────────────────────────────────────────
    private final AtomicBoolean running = new AtomicBoolean(false);
    private CandleService candleService;
    private StrategyEvaluator strategyEvaluator;
    private PositionMonitor positionMonitor;
    private HealthMonitor healthMonitor;
    private PositionReconciler positionReconciler;

    // ── Factory ───────────────────────────────────────────────────────────────

    private TradingEngine(ExecutionMode mode, IOrderExecutor executor,
                          RiskManager riskManager, TradeJournal journal,
                          ConfigManager config) {
        this.mode = mode;
        this.executor = executor;
        this.riskManager = riskManager;
        this.journal = journal;
        this.config = config;
    }

    /**
     * Creates a fully wired TradingEngine.
     * The Fyers WebSocket (Thread 1) must be started separately by the caller so
     * that {@code FyersSocketListener.OnScrips} feeds into {@link TickStore}.
     */
    public static TradingEngine create() {
        ConfigManager config = ConfigManager.getInstance();
        RiskConfig riskCfg = config.getRiskConfig();
        TickStore tickStore = TickStore.getInstance();

        ExecutionMode mode = resolveMode(config.getProperty("APP_ENV"));
        IOrderExecutor executor = createExecutor(mode, tickStore);

        TradeJournal journal = new TradeJournal(mode);
        RiskManager riskMgr = new RiskManager(riskCfg);

        TradingEngine engine = new TradingEngine(mode, executor, riskMgr, journal, config);

        // Queue between Thread 2 (candle workers) and Thread 3 (strategy evaluator)
        LinkedBlockingQueue<CandleRecommendation> recQueue =
                new LinkedBlockingQueue<>(REC_QUEUE_CAPACITY);

        // Thread 4: position monitor — constructed before StrategyEvaluator;
        //           StrategyEvaluator injected via setter to break circular dependency.
        PositionMonitor pm = new PositionMonitor(
                tickStore, riskCfg, engine::handleExit, null);
        engine.positionMonitor = pm;

        // Thread 3: strategy evaluator
        StrategyEvaluator se = new StrategyEvaluator(
                recQueue, engine::handleSignal, riskCfg, pm);
        pm.setStrategyEvaluator(se);    // complete the cycle
        engine.strategyEvaluator = se;

        // Thread 2: candle workers
        CandleService cs = new CandleService(tickStore, recQueue);
        engine.candleService = cs;

        // Thread 5: health monitor
        HealthMonitor hm = new HealthMonitor(
                tickStore, cs, se, pm, config.getActiveSymbols());
        engine.healthMonitor = hm;

        // Position reconciler — only meaningful in LIVE mode
        if (mode == ExecutionMode.LIVE) {
            engine.positionReconciler = new PositionReconciler(
                    new FyersPositions(), pm, engine.openRecords, journal, riskCfg);
        }

        log.info("TradingEngine created — mode={} symbols={}",
                mode, String.join(",", config.getActiveSymbols()));
        return engine;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private static ExecutionMode resolveMode(String appEnv) {
        if (appEnv == null) return ExecutionMode.PAPER;
        return switch (appEnv.trim().toUpperCase()) {
            case "LIVE" -> ExecutionMode.LIVE;
            case "BACKTEST" -> ExecutionMode.BACKTEST;
            default -> ExecutionMode.PAPER;
        };
    }

    private static IOrderExecutor createExecutor(ExecutionMode mode, TickStore tickStore) {
        return switch (mode) {
            case LIVE -> {
                log.warn("LIVE mode selected — real orders will be placed. Ensure go-live checklist is complete.");
                yield new LiveOrderExecutor();
            }
            case BACKTEST -> new BacktestOrderExecutor();
            default -> {
                log.info("PAPER mode — orders will be simulated at live price");
                yield new PaperOrderExecutor(tickStore);
            }
        };
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("TradingEngine already running");
            return;
        }
        log.info("TradingEngine starting in {} mode...", mode);

        // ── Position reconciliation (LIVE mode only) ────────────────────────────
        if (positionReconciler != null) {
            log.info("Running position reconciliation before startup...");
            PositionReconciler.ReconciliationResult result = positionReconciler.reconcile();
            log.info("Reconciliation result: {}", result);
        }

        positionMonitor.start();                         // Thread 4 first — must be ready before signals fire
        strategyEvaluator.start();                       // Thread 3
        candleService.start(config.getActiveSymbols());  // Thread 2
        healthMonitor.start();                           // Thread 5

        registerShutdownHook();
        log.info("TradingEngine started. Active symbols: {}",
                String.join(", ", config.getActiveSymbols()));
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        log.info("TradingEngine stopping...");
        healthMonitor.stop();
        candleService.stop();
        strategyEvaluator.stop();
        positionMonitor.stop();
        log.info("TradingEngine stopped. Journal: {} closed trades",
                journal.closedTradeCount());
    }

    public boolean isRunning() {
        return running.get();
    }

    public CandleService getCandleService() {
        return candleService;
    }

    public StrategyEvaluator getStrategyEvaluator() {
        return strategyEvaluator;
    }

    public PositionMonitor getPositionMonitor() {
        return positionMonitor;
    }

    public HealthMonitor getHealthMonitor() {
        return healthMonitor;
    }

    public RiskManager getRiskManager() {
        return riskManager;
    }

    public TradeJournal getJournal() {
        return journal;
    }

    public PositionReconciler getPositionReconciler() {
        return positionReconciler;
    }

    // ── Signal handler (Thread 3 → entry pipeline) ───────────────────────────

    public ExecutionMode getMode() {
        return mode;
    }

    // ── Exit handler (Thread 4 → exit pipeline) ───────────────────────────────

    /** Run strategy analyzer on all trades closed this session. */
    public StrategyAnalyzer.Report analyzeSession() {
        return StrategyAnalyzer.analyze(journal.closedTrades());
    }

    // ── Mode resolution ───────────────────────────────────────────────────────

    /**
     * Called by StrategyEvaluator when a compound signal passes all strategy gates.
     * Runs: pre-trade risk check → position size → place entry order → register position.
     */
    private void handleSignal(TradeSignal signal) {
        log.info("[{}] Signal received: {}", signal.getSymbol(), signal);
        journal.logSignalGenerated(signal);

        // ── 1. Pre-trade risk check ───────────────────────────────────────────
        RiskManager.PreTradeResult check = riskManager.preTradeCheck(
                signal,
                positionMonitor.openPositions(),
                config.getRiskConfig().getInitialCapitalInr());

        if (!check.approved()) {
            log.info("[{}] Signal REJECTED by risk: {}", signal.getSymbol(), check.rejectReason());
            journal.logSignalRejected(signal, check.rejectReason());
            return;
        }

        // ── 2. Place entry order ──────────────────────────────────────────────
        OrderFill fill = executor.placeEntry(signal, check.quantity());
        journal.logOrderEntry(signal, fill);

        if (!fill.isSuccess()) {
            log.warn("[{}] Entry order REJECTED by executor: {}", signal.getSymbol(), fill.getRejectReason());
            journal.logSignalRejected(signal, "Order executor rejected: " + fill.getRejectReason());
            return;
        }

        // ── 3. Create open position and register with monitor ─────────────────
        OpenPosition pos = new OpenPosition(
                signal.getSymbol(),
                signal.getCorrelationId(),
                signal.getStrategyId(),
                signal.getDirection(),
                fill.getFillPrice(),
                fill.getFillQuantity(),
                check.stopLoss(),
                check.takeProfit(),
                fill.getFillTime());

        // ── 4. Create trade record (entry half) ───────────────────────────────
        double entryAtr = Math.abs(signal.getSuggestedEntry() - signal.getSuggestedStopLoss()) / 2.0;
        TradeRecord record = new TradeRecord(
                signal.getCorrelationId(),
                signal.getSymbol(),
                signal.getStrategyId(),
                mode,
                signal.getDirection(),
                fill.getFillPrice(),
                fill.getFillQuantity(),
                check.stopLoss(),
                check.takeProfit(),
                fill.getFillTime(),
                entryAtr,
                signal.getConfidence(),
                signal.getTimeframeVotes());

        openRecords.put(signal.getCorrelationId(), record);
        positionMonitor.addPosition(pos);

        log.info("[{}] Position OPENED: {}", signal.getSymbol(), pos);
    }

    /**
     * Called by PositionMonitor when SL, TP, trailing stop, or time-based exit fires.
     * Runs: place exit order → close trade record → update risk accounting → journal.
     */
    private void handleExit(OpenPosition position, PositionMonitor.ExitReason reason) {
        // Determine the trigger price based on exit reason
        double triggerPrice = switch (reason) {
            case STOP_LOSS, TRAILING_STOP -> position.getCurrentStopLoss();
            case TAKE_PROFIT -> position.getTakeProfit();
            default -> 0; // executor uses live price
        };

        log.info("[{}] Exit triggered: reason={} triggerPrice={}",
                position.getSymbol(), reason, String.format("%.2f", triggerPrice));

        // ── 1. Place exit order ───────────────────────────────────────────────
        OrderFill fill = executor.placeExit(position, reason, triggerPrice);
        journal.logOrderExit(position, fill, reason);

        double actualExit = fill.isSuccess() ? fill.getFillPrice() : triggerPrice;
        Instant exitTime = fill.isSuccess() ? fill.getFillTime() : Instant.now();

        if (!fill.isSuccess()) {
            log.error("[{}] Exit order FAILED: {} — using trigger price as fill",
                    position.getSymbol(), fill.getRejectReason());
        }

        // ── 2. Close trade record ─────────────────────────────────────────────
        TradeRecord record = openRecords.remove(position.getCorrelationId());
        if (record != null) {
            record.close(actualExit, exitTime, reason);
            riskManager.recordClosedTrade(record);
            journal.logTradeClosed(record);

            log.info("[{}] Trade CLOSED: pnl={} R={} reason={}",
                    position.getSymbol(),
                    String.format("%.2f", record.getPnl()),
                    String.format("%.2f", record.getRMultipleAchieved()),
                    reason);
        } else {
            log.warn("[{}] No TradeRecord found for correlationId={}",
                    position.getSymbol(), position.getCorrelationId());
        }
    }

    // ── Shutdown hook ─────────────────────────────────────────────────────────

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(
                Thread.ofVirtual().name("engine-shutdown-hook").unstarted(() -> {
                    log.info("JVM shutdown hook — stopping TradingEngine");
                    stop();
                    // Print session report on clean shutdown
                    StrategyAnalyzer.Report report = analyzeSession();
                    if (report.totalTrades() > 0) {
                        log.info(report.summary());
                    }
                }));
    }
}
