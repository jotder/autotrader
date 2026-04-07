package com.rj.engine;

import com.rj.engine.disruptor.TickDisruptorEngine;
import com.rj.engine.disruptor.TickStoreUpdater;
import com.rj.engine.risk.PreTradeGate;
import com.rj.engine.risk.PreTradeResult;
import com.rj.engine.risk.RiskSessionState;
import com.rj.fyers.FyersSocketListener;
import com.rj.config.*;
import com.rj.model.*;
import com.rj.broker.IOrderAdapter;
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
    private final PreTradeGate preTradeGate;
    private final RiskSessionState riskSessionState;
    private final PositionBook positionBook;
    private final TickRiskProcessor tickRiskProcessor;
    private final ScheduledPositionManager scheduledPositionManager;
    private final TradeJournal journal;
    private final ConfigManager config;
    private final TickDisruptorEngine disruptorEngine;
    private final FyersSocketListener socketListener;

    /**
     * Open trade records keyed by correlationId.
     * Created on SUBMITTED; removed and closed on exit FILLED.
     */
    private final ConcurrentHashMap<String, TradeRecord> openRecords;

    // ── Services ──────────────────────────────────────────────────────────────
    private final AtomicBoolean running = new AtomicBoolean(false);
    private CandleService candleService;
    private StrategyEvaluator strategyEvaluator;
    private HealthMonitor healthMonitor;
    private PositionReconciler positionReconciler;
    private ConfigFileWatcher configFileWatcher;
    private AnomalyDetector anomalyDetector;
    private BrokerCircuitBreaker circuitBreaker;

    // ── Public constructor (used by EngineConfiguration) ─────────────────────

    public TradingEngine(ExecutionMode mode, IOrderExecutor executor, OrderManager orderManager,
                  PreTradeGate preTradeGate, RiskSessionState riskSessionState,
                  PositionBook positionBook, TickRiskProcessor tickRiskProcessor,
                  ScheduledPositionManager scheduledPositionManager,
                  StrategyEvaluator strategyEvaluator, CandleService candleService,
                  AnomalyDetector anomalyDetector, BrokerCircuitBreaker circuitBreaker,
                  HealthMonitor healthMonitor, PositionReconciler positionReconciler,
                  TradeJournal journal, ConfigManager config,
                  TickDisruptorEngine disruptorEngine, FyersSocketListener socketListener,
                  ConcurrentHashMap<String, TradeRecord> openRecords) {
        this.mode = mode;
        this.executor = executor;
        this.orderManager = orderManager;
        this.preTradeGate = preTradeGate;
        this.riskSessionState = riskSessionState;
        this.positionBook = positionBook;
        this.tickRiskProcessor = tickRiskProcessor;
        this.scheduledPositionManager = scheduledPositionManager;
        this.strategyEvaluator = strategyEvaluator;
        this.candleService = candleService;
        this.anomalyDetector = anomalyDetector;
        this.circuitBreaker = circuitBreaker;
        this.healthMonitor = healthMonitor;
        this.positionReconciler = positionReconciler;
        this.journal = journal;
        this.config = config;
        this.disruptorEngine = disruptorEngine;
        this.socketListener = socketListener;
        this.openRecords = openRecords;
    }

    // ── Static helpers kept for EngineConfiguration use ──────────────────────

    public static ExecutionMode resolveMode(String appEnv) {
        if (appEnv == null) return ExecutionMode.PAPER;
        return switch (appEnv.trim().toUpperCase()) {
            case "LIVE" -> ExecutionMode.LIVE;
            case "BACKTEST" -> ExecutionMode.BACKTEST;
            default -> ExecutionMode.PAPER;
        };
    }

    public static IOrderExecutor createExecutor(ExecutionMode mode, TickStore tickStore, IOrderAdapter orderAdapter) {
        return switch (mode) {
            case LIVE -> new LiveOrderExecutor(orderAdapter);
            case BACKTEST -> new BacktestOrderExecutor();
            default -> new PaperOrderExecutor(tickStore);
        };
    }

    // ── Strategy loading (called by EngineConfiguration) ─────────────────────

    public void loadYamlStrategies(CandleService cs, StrategyEvaluator se) {
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
                    preTradeGate.applyStrategyRiskOverride(entry.getKey(), riskOverride);
                }
                log.info("Loaded {} strategy configs from YAML at startup", initialConfigs.size());

                ConfigValidator validator = new ConfigValidator(config.getSymbolRegistry());
                this.configFileWatcher = new ConfigFileWatcher(
                        strategiesDir, strategiesPath, defaultsPath,
                        loader, validator,
                        newStrategies -> {
                            for (Map.Entry<String, StrategyYamlConfig> entry : newStrategies.entrySet()) {
                                StrategyRiskConfig riskOverride = StrategyRiskConfig.from(entry.getValue().getRisk());
                                preTradeGate.applyStrategyRiskOverride(entry.getKey(), riskOverride);
                            }
                            se.updateStrategyConfigs(newStrategies);
                            log.info("Hot-reload applied: {} strategy configs updated", newStrategies.size());
                        });
            } catch (Exception e) {
                log.warn("Failed to load strategy YAML configs at startup: {}", e.getMessage());
            }
        }
    }

    public void initializePluggableStrategies(StrategyEvaluator se) {
        // Phase-II: Register the default voting strategy
        var defaultStrategy = new com.rj.strategy.MultiTimeframeVotingStrategy(
                "trend_following",
                "Trend Following (Phase-I Port)",
                0.70, // minConfidence
                2.0,  // slAtrMultiplier
                2.0   // tpRMultiple
        );
        se.addStrategy(defaultStrategy);

        // Register configuration for this strategy in PreTradeGate
        TradeStrategyConfig stratCfg = new TradeStrategyConfig();
        stratCfg.setStrategyId(defaultStrategy.getId());
        stratCfg.setName(defaultStrategy.getName());
        stratCfg.setActive(true);
        stratCfg.setAllocationPercentage(100.0);
        stratCfg.setSizingType(com.rj.model.SizingType.VOLATILITY_ATR);
        stratCfg.setRiskPercentage(1.0);
        stratCfg.setAtrMultiplier(2.0);

        preTradeGate.updateStrategyConfig(stratCfg);
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

        positionBook.add(pos);
        journal.logOrderEntry(null, order.toOrderFill());
    }

    private void handleExitFilled(ManagedOrder order) {
        log.info("[{}] EXIT FILLED: {} @ {}", order.getSymbol(), order.getFilledQuantity(), order.getFillPrice());

        TradeRecord record = openRecords.remove(order.getCorrelationId());
        if (record != null) {
            ExitReason reason = ExitReason.MANUAL;
            if (order.getRejectReason() != null && order.getRejectReason().startsWith("reason=")) {
                try { reason = ExitReason.valueOf(order.getRejectReason().substring(7)); } catch (Exception ignored) {}
            }

            record.close(order.getFillPrice(), order.getLastUpdatedAt(), reason);
            riskSessionState.recordClosedTrade(record);
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

    public void handleSignal(TradeSignal signal) {
        log.info("[{}] Signal received: {}", signal.getSymbol(), signal);
        journal.logSignalGenerated(signal);

        PreTradeResult check = preTradeGate.preTradeCheck(
                signal, positionBook.openPositions(), config.getRiskConfig().getInitialCapitalInr());

        if (!check.approved()) {
            log.info("[{}] Signal REJECTED: {}", signal.getSymbol(), check.rejectReason());
            journal.logSignalRejected(signal, check.rejectReason());
            return;
        }

        double entryAtr = Math.abs(signal.getSuggestedEntry() - signal.getSuggestedStopLoss()) / 2.0;
        TradeRecord record = new TradeRecord(
                signal.getCorrelationId(), signal.getSymbol(), signal.getStrategyId(),
                mode, signal.getDirection(), 0, 0, check.stopLoss(), check.takeProfit(),
                Instant.now(), entryAtr, signal.getConfidence(), signal.getTimeframeVotes());
        openRecords.put(signal.getCorrelationId(), record);

        orderManager.submitEntry(signal, check.quantity());
    }

    public void handleExit(OpenPosition position, ExitReason reason) {
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
        scheduledPositionManager.start();
        anomalyDetector.start();
        strategyEvaluator.start();
        candleService.start(config.getActiveSymbols());
        healthMonitor.start();

        if (mode != ExecutionMode.BACKTEST) {
            socketListener.startWebSocket();
            socketListener.subscribe(java.util.Arrays.asList(config.getActiveSymbols()));
        }

        if (configFileWatcher != null) try { configFileWatcher.start(); } catch (IOException ignored) {}

        registerShutdownHook();
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        log.info("TradingEngine stopping...");
        if (configFileWatcher != null) configFileWatcher.stop();
        socketListener.close();
        orderManager.shutdown();
        healthMonitor.stop();
        candleService.stop();
        strategyEvaluator.stop();
        anomalyDetector.stop();
        scheduledPositionManager.stop();
        disruptorEngine.stop();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isRunning() { return running.get(); }
    public ExecutionMode getMode() { return mode; }
    public TradeJournal getJournal() { return journal; }
    public OrderTracker getOrderTracker() { return orderManager.getTracker(); }
    public BrokerCircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public TickDisruptorEngine getDisruptorEngine() { return disruptorEngine; }
    public FyersSocketListener getSocketListener() { return socketListener; }

    // New decomposed getters
    public PreTradeGate getPreTradeGate() { return preTradeGate; }
    public RiskSessionState getRiskSessionState() { return riskSessionState; }
    public PositionBook getPositionBook() { return positionBook; }
    public ScheduledPositionManager getScheduledPositionManager() { return scheduledPositionManager; }

    // REST API Accessors
    public HealthMonitor getHealthMonitor() { return healthMonitor; }
    public CandleService getCandleService() { return candleService; }
    public StrategyEvaluator getStrategyEvaluator() { return strategyEvaluator; }
    public PositionReconciler getPositionReconciler() { return positionReconciler; }
    public AnomalyDetector getAnomalyDetector() { return anomalyDetector; }

    public int flattenAll(String reason) {
        riskSessionState.triggerAnomaly(reason);
        return scheduledPositionManager.closeAllPositions(ExitReason.ANOMALY_FLATTEN);
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
