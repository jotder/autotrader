package com.rj.engine;

import com.rj.engine.disruptor.TickDisruptorEngine;
import com.rj.engine.disruptor.TickStoreUpdater;
import com.rj.fyers.FyersSocketListener;
import com.rj.config.*;
import com.rj.model.*;
import com.rj.fyers.FyersPositions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main orchestrator — wires all service threads and manages their lifecycle.
 */
public class TradingEngine implements OrderStateListener {

    private static final Logger log = LoggerFactory.getLogger(TradingEngine.class);
    private static final int REC_QUEUE_CAPACITY = 2048;

    // ── Core dependencies ─────────────────────────────────────────────────────

    private final ExecutionMode mode;
    private final IOrderExecutor executor;
    private final OrderManager orderManager;
    private final RiskManager riskManager;
    private final TradeJournal journal;
    private final ConfigManager config;
    private final TickDisruptorEngine disruptorEngine;
    private final FyersSocketListener socketListener;

    /**
     * Open trade records keyed by correlationId.
     * Created on SUBMITTED; removed and closed on exit FILLED.
     */
    private final ConcurrentHashMap<String, TradeRecord> openRecords = new ConcurrentHashMap<>();

    // ── Services ──────────────────────────────────────────────────────────────
    private final AtomicBoolean running = new AtomicBoolean(false);
    private CandleService candleService;
    private StrategyEvaluator strategyEvaluator;
    private PositionMonitor positionMonitor;
    private HealthMonitor healthMonitor;
    private PositionReconciler positionReconciler;
    private ConfigFileWatcher configFileWatcher;
    private com.rj.fyers.TokenRefreshScheduler tokenRefreshScheduler;
    private AnomalyDetector anomalyDetector;
    private BrokerCircuitBreaker circuitBreaker;

    // ── Factory ───────────────────────────────────────────────────────────────

    private TradingEngine(ExecutionMode mode, IOrderExecutor executor,
                          RiskManager riskManager, TradeJournal journal,
                          ConfigManager config, TickDisruptorEngine disruptorEngine,
                          FyersSocketListener socketListener) {
        this.mode = mode;
        this.executor = executor;
        this.riskManager = riskManager;
        this.journal = journal;
        this.config = config;
        this.disruptorEngine = disruptorEngine;
        this.socketListener = socketListener;

        // OMS state machine wraps the raw executor
        var orderTracker = new OrderTracker(java.time.Duration.ofSeconds(30));
        this.orderManager = new OrderManager(executor, orderTracker, journal);
    }

    /**
     * Creates a fully wired TradingEngine.
     */
    public static TradingEngine create() {
        ConfigManager config = ConfigManager.getInstance();
        RiskConfig riskCfg = config.getRiskConfig();
        TickStore tickStore = TickStore.getInstance();

        ExecutionMode mode = resolveMode(config.getProperty("APP_ENV"));
        IOrderExecutor executor = createExecutor(mode, tickStore);

        TradeJournal journal = new TradeJournal(mode);
        RiskManager riskMgr = new RiskManager(riskCfg);

        // Disruptor & WebSocket Ingestion
        TickDisruptorEngine disruptor = new TickDisruptorEngine();
        FyersSocketListener listener = new FyersSocketListener(disruptor, null); // Wired below

        TradingEngine engine = new TradingEngine(mode, executor, riskMgr, journal, config, disruptor, listener);

        // Fix circular dependency for listener -> manager -> engine
        FyersSocketListener updatedListener = new FyersSocketListener(disruptor, engine.orderManager);
        TradingEngine engineFinal = new TradingEngine(mode, executor, riskMgr, journal, config, disruptor, updatedListener);

        // Queue between Thread 4 (candle workers) and Thread 5 (strategy evaluator)
        LinkedBlockingQueue<CandleRecommendation> recQueue =
                new LinkedBlockingQueue<>(REC_QUEUE_CAPACITY);

        // Position Monitor — Disruptor Handler 2
        PositionMonitor pm = new PositionMonitor(
                tickStore, riskCfg, engineFinal::handleExit, null);
        engineFinal.positionMonitor = pm;

        // Register Disruptor Handlers
        disruptor.addHandler(new TickStoreUpdater());
        disruptor.addHandler(pm);

        // Strategy Evaluator
        StrategyEvaluator se = new StrategyEvaluator(
                recQueue, engineFinal::handleSignal, riskCfg, pm);
        pm.setStrategyEvaluator(se);    // complete the cycle
        engineFinal.strategyEvaluator = se;

        // Candle Workers
        CandleService cs = new CandleService(tickStore, recQueue, config);
        engineFinal.candleService = cs;

        // Anomaly detector
        AnomalyDetector ad = new AnomalyDetector();
        ad.initialize(riskMgr, pm, tickStore, journal, riskCfg);
        engineFinal.anomalyDetector = ad;

        // Circuit breaker
        var cbConfig = com.rj.config.CircuitBreakerConfig.fromEnvironment(config::getProperty);
        BrokerCircuitBreaker cb = new BrokerCircuitBreaker(cbConfig, ad);
        engineFinal.circuitBreaker = cb;

        if (executor instanceof LiveOrderExecutor loe) {
            loe.setCircuitBreaker(cb);
        }

        // Health monitor
        HealthMonitor hm = new HealthMonitor(
                tickStore, cs, se, pm, config.getActiveSymbols());
        engineFinal.healthMonitor = hm;

        if (mode == ExecutionMode.LIVE) {
            engineFinal.positionReconciler = new PositionReconciler(
                    new FyersPositions(), pm, engineFinal.openRecords, journal, riskCfg);
        }

        // OMS Listener
        engineFinal.orderManager.getTracker().addListener(engineFinal);

        // Load YAML strategies...
        engineFinal.loadYamlStrategies(cs, se, riskMgr);

        log.info("TradingEngine created — mode={} symbols={}",
                mode, String.join(",", config.getActiveSymbols()));
        return engineFinal;
    }

    private void loadYamlStrategies(CandleService cs, StrategyEvaluator se, RiskManager riskMgr) {
        Path strategiesDir = Path.of("config/strategies");
        Path strategiesPath = Path.of("config/strategies/intraday.yaml");
        Path defaultsPath = Path.of("config/defaults.yaml");
        if (Files.isDirectory(strategiesDir) && Files.exists(strategiesPath)) {
            try {
                YamlStrategyLoader loader = new YamlStrategyLoader();
                Map<String, StrategyYamlConfig> initialConfigs = loader.loadWithDefaults(strategiesPath, defaultsPath);
                cs.setStrategyConfigs(initialConfigs);
                se.updateStrategyConfigs(initialConfigs);

                for (Map.Entry<String, StrategyYamlConfig> entry : initialConfigs.entrySet()) {
                    StrategyRiskConfig riskOverride = StrategyRiskConfig.from(entry.getValue().getRisk());
                    riskMgr.applyStrategyRiskOverride(entry.getKey(), riskOverride);
                }
                log.info("Loaded {} strategy configs from YAML at startup", initialConfigs.size());

                ConfigValidator validator = new ConfigValidator(config.getSymbolRegistry());
                this.configFileWatcher = new ConfigFileWatcher(
                        strategiesDir, strategiesPath, defaultsPath,
                        loader, validator,
                        newStrategies -> {
                            for (Map.Entry<String, StrategyYamlConfig> entry : newStrategies.entrySet()) {
                                StrategyRiskConfig riskOverride = StrategyRiskConfig.from(entry.getValue().getRisk());
                                riskMgr.applyStrategyRiskOverride(entry.getKey(), riskOverride);
                            }
                            se.updateStrategyConfigs(newStrategies);
                            log.info("Hot-reload applied: {} strategy configs updated", newStrategies.size());
                        });
            } catch (Exception e) {
                log.warn("Failed to load strategy YAML configs at startup: {}", e.getMessage());
            }
        }
    }

    // ── OrderStateListener ───────────────────────────────────────────────────

    @Override
    public void onStateChange(ManagedOrder order, ManagedOrder.StateTransition transition) {
        OrderState newState = transition.to();
        log.debug("[OMS][{}] State: {} -> {}", order.getSymbol(), transition.from(), newState);

        if (newState == OrderState.FILLED) {
            if (order.getSide() == OrderSideType.ENTRY) {
                handleEntryFilled(order);
            } else {
                handleExitFilled(order);
            }
        } else if (newState == OrderState.REJECTED || newState == OrderState.EXPIRED) {
            handleOrderFailed(order, newState);
        }
    }

    private void handleEntryFilled(ManagedOrder order) {
        log.info("[{}] ENTRY FILLED: {} @ {}", order.getSymbol(), order.getFilledQuantity(), order.getFillPrice());
        
        TradeRecord record = openRecords.get(order.getCorrelationId());
        double sl = record != null ? record.getInitialStopLoss() : order.getFillPrice() * 0.99;
        double tp = record != null ? record.getTakeProfit() : order.getFillPrice() * 1.02;

        OpenPosition pos = new OpenPosition(
                order.getSymbol(),
                order.getCorrelationId(),
                order.getStrategyId(),
                order.getDirection(),
                order.getFillPrice(),
                order.getFilledQuantity(),
                sl, tp,
                order.getLastUpdatedAt());

        positionMonitor.addPosition(pos);
        journal.logOrderEntry(null, order.toOrderFill());
    }

    private void handleExitFilled(ManagedOrder order) {
        log.info("[{}] EXIT FILLED: {} @ {}", order.getSymbol(), order.getFilledQuantity(), order.getFillPrice());
        
        TradeRecord record = openRecords.remove(order.getCorrelationId());
        if (record != null) {
            PositionMonitor.ExitReason reason = PositionMonitor.ExitReason.MANUAL;
            if (order.getRejectReason() != null && order.getRejectReason().startsWith("reason=")) {
                try { reason = PositionMonitor.ExitReason.valueOf(order.getRejectReason().substring(7)); } catch (Exception ignored) {}
            }

            record.close(order.getFillPrice(), order.getLastUpdatedAt(), reason);
            riskManager.recordClosedTrade(record);
            journal.logTradeClosed(record);
            
            log.info("[{}] Trade CLOSED: pnl={} R={}", order.getSymbol(), 
                    String.format("%.2f", record.getPnl()), 
                    String.format("%.2f", record.getRMultipleAchieved()));
        }
    }

    private void handleOrderFailed(ManagedOrder order, OrderState state) {
        log.warn("[{}] ORDER {}: {} reason={}", order.getSymbol(), state, order.getClientOrderId(), order.getRejectReason());
        if (order.getSide() == OrderSideType.ENTRY) {
            openRecords.remove(order.getCorrelationId());
        }
    }

    // ── Lifecycle & Handlers ─────────────────────────────────────────────────

    private void handleSignal(TradeSignal signal) {
        log.info("[{}] Signal received: {}", signal.getSymbol(), signal);
        journal.logSignalGenerated(signal);

        RiskManager.PreTradeResult check = riskManager.preTradeCheck(
                signal, positionMonitor.openPositions(), config.getRiskConfig().getInitialCapitalInr());

        if (!check.approved()) {
            log.info("[{}] Signal REJECTED: {}", signal.getSymbol(), check.rejectReason());
            journal.logSignalRejected(signal, check.rejectReason());
            return;
        }

        // Initialize TradeRecord placeholder
        double entryAtr = Math.abs(signal.getSuggestedEntry() - signal.getSuggestedStopLoss()) / 2.0;
        TradeRecord record = new TradeRecord(
                signal.getCorrelationId(), signal.getSymbol(), signal.getStrategyId(),
                mode, signal.getDirection(), 0, 0, check.stopLoss(), check.takeProfit(),
                Instant.now(), entryAtr, signal.getConfidence(), signal.getTimeframeVotes());
        openRecords.put(signal.getCorrelationId(), record);

        orderManager.submitEntry(signal, check.quantity());
    }

    private void handleExit(OpenPosition position, PositionMonitor.ExitReason reason) {
        double triggerPrice = switch (reason) {
            case STOP_LOSS, TRAILING_STOP -> position.getCurrentStopLoss();
            case TAKE_PROFIT -> position.getTakeProfit();
            default -> 0;
        };
        log.info("[{}] Exit triggered: reason={} price={}", position.getSymbol(), reason, triggerPrice);
        orderManager.submitExit(position, reason, triggerPrice);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        log.info("TradingEngine starting in {} mode...", mode);

        if (positionReconciler != null) {
            positionReconciler.reconcile();
        }

        disruptorEngine.start();
        positionMonitor.start();
        anomalyDetector.start();
        strategyEvaluator.start();
        candleService.start(config.getActiveSymbols());
        healthMonitor.start();

        if (mode != ExecutionMode.BACKTEST) {
            socketListener.startWebSocket();
            socketListener.subscribe(java.util.Arrays.asList(config.getActiveSymbols()));
        }

        if (configFileWatcher != null) try { configFileWatcher.start(); } catch (IOException ignored) {}
        tokenRefreshScheduler = new com.rj.fyers.TokenRefreshScheduler(config);
        tokenRefreshScheduler.start();

        registerShutdownHook();
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        log.info("TradingEngine stopping...");
        if (configFileWatcher != null) configFileWatcher.stop();
        if (tokenRefreshScheduler != null) tokenRefreshScheduler.stop();
        socketListener.close();
        orderManager.shutdown();
        healthMonitor.stop();
        candleService.stop();
        strategyEvaluator.stop();
        anomalyDetector.stop();
        positionMonitor.stop();
        disruptorEngine.stop();
    }

    // ── Standard Boilerplate ─────────────────────────────────────────────────

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
            case LIVE -> new LiveOrderExecutor();
            case BACKTEST -> new BacktestOrderExecutor();
            default -> new PaperOrderExecutor(tickStore);
        };
    }

    public boolean isRunning() { return running.get(); }
    public ExecutionMode getMode() { return mode; }
    public RiskManager getRiskManager() { return riskManager; }
    public TradeJournal getJournal() { return journal; }
    public OrderTracker getOrderTracker() { return orderManager.getTracker(); }
    public BrokerCircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public TickDisruptorEngine getDisruptorEngine() { return disruptorEngine; }
    public FyersSocketListener getSocketListener() { return socketListener; }
    
    // REST API Accessors
    public PositionMonitor getPositionMonitor() { return positionMonitor; }
    public HealthMonitor getHealthMonitor() { return healthMonitor; }
    public CandleService getCandleService() { return candleService; }
    public StrategyEvaluator getStrategyEvaluator() { return strategyEvaluator; }
    public PositionReconciler getPositionReconciler() { return positionReconciler; }
    public com.rj.fyers.TokenRefreshScheduler getTokenRefreshScheduler() { return tokenRefreshScheduler; }
    public AnomalyDetector getAnomalyDetector() { return anomalyDetector; }

    public int flattenAll(String reason) {
        riskManager.triggerAnomaly(reason);
        return positionMonitor.closeAllPositions(PositionMonitor.ExitReason.ANOMALY_FLATTEN);
    }

    public StrategyAnalyzer.Report analyzeSession() {
        return StrategyAnalyzer.analyze(journal.closedTrades());
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() -> {
            stop();
            var report = analyzeSession();
            if (report.totalTrades() > 0) log.info(report.summary());
        }));
    }
}
