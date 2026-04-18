package com.rj.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rj.broker.IMarketDataAdapter;
import com.rj.broker.IOrderAdapter;
import com.rj.engine.*;
import com.rj.engine.disruptor.TickDisruptorEngine;
import com.rj.engine.disruptor.TickStoreUpdater;
import com.rj.engine.options.OptionChainService;
import com.rj.engine.risk.PreTradeGate;
import com.rj.engine.risk.RiskSessionState;
import com.rj.fyers.FyersSocketListener;
import com.rj.model.CandleRecommendation;
import com.rj.model.ExecutionMode;
import com.rj.model.TickStore;
import com.rj.model.TradeRecord;
import com.tts.in.model.FyersClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Configuration
public class EngineConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EngineConfiguration.class);
    private static final int REC_QUEUE_CAPACITY = 2048;

    @Bean
    public FyersClass fyersClass() {
        return FyersClass.getInstance();
    }

    @Bean
    public TickStore tickStore() {
        return TickStore.getInstance();
    }

    @Bean
    public RiskConfig riskConfig(ConfigManager configManager) {
        return configManager.getRiskConfig();
    }

    @Bean
    public StrategyConfig strategyConfig(ConfigManager configManager) {
        return configManager.getStrategyConfig();
    }

    @Bean
    public RiskSessionState riskSessionState(RiskConfig riskConfig) {
        return new RiskSessionState(riskConfig);
    }

    @Bean
    public PreTradeGate preTradeGate(RiskConfig riskConfig, RiskSessionState riskSessionState) {
        return new PreTradeGate(riskConfig, riskSessionState);
    }

    @Bean
    public PositionBook positionBook() {
        return new PositionBook();
    }

    @Bean
    public TradingEngine tradingEngine(ConfigManager config,
                                       IOrderAdapter orderAdapter,
                                       PreTradeGate preTradeGate,
                                       RiskSessionState riskSessionState,
                                       PositionBook positionBook,
                                       OptionChainService optionChainService) {

        ExecutionMode mode = TradingEngine.resolveMode(config.getProperty("APP_ENV"));
        TickStore tickStore = TickStore.getInstance();
        RiskConfig riskConfig = config.getRiskConfig();

        IOrderExecutor executor = TradingEngine.createExecutor(mode, tickStore, orderAdapter);
        TradeJournal journal = new TradeJournal(mode);
        TickDisruptorEngine disruptor = new TickDisruptorEngine();

        ConcurrentHashMap<String, TradeRecord> openRecords = new ConcurrentHashMap<>();
        OrderTracker orderTracker = new OrderTracker(Duration.ofSeconds(30));
        OrderManager orderManager = new OrderManager(executor, orderTracker, journal);

        // FyersSocketListener has final OrderManager field — create after OrderManager
        FyersSocketListener socketListener = new FyersSocketListener(disruptor, orderManager);

        LinkedBlockingQueue<CandleRecommendation> recQueue =
                new LinkedBlockingQueue<>(REC_QUEUE_CAPACITY);

        // Step 1: construct components that don't yet have all callbacks
        TickRiskProcessor tickRiskProcessor =
                new TickRiskProcessor(positionBook, riskSessionState, riskConfig);
        ScheduledPositionManager scheduledPositionManager =
                new ScheduledPositionManager(positionBook, riskSessionState, riskConfig, tickStore);
        StrategyEvaluator se = new StrategyEvaluator(recQueue, null, riskConfig, positionBook);
        CandleService cs = new CandleService(tickStore, recQueue, config);

        AnomalyDetector ad = new AnomalyDetector();
        CircuitBreakerConfig cbConfig =
                CircuitBreakerConfig.fromEnvironment(config::getProperty);
        BrokerCircuitBreaker cb = new BrokerCircuitBreaker(cbConfig, ad);
        if (executor instanceof LiveOrderExecutor loe) loe.setCircuitBreaker(cb);

        HealthMonitor hm = new HealthMonitor(tickStore, cs, se,
                scheduledPositionManager, positionBook, config.getActiveSymbols());

        PositionReconciler reconciler = null;
        if (mode == ExecutionMode.LIVE) {
            reconciler = new PositionReconciler(
                    orderAdapter, positionBook, openRecords, journal, riskConfig);
        }

        // Step 2: create TradingEngine with all deps
        TradingEngine engine = new TradingEngine(
                mode, executor, orderManager,
                preTradeGate, riskSessionState, positionBook,
                tickRiskProcessor, scheduledPositionManager,
                se, cs, ad, cb, hm, reconciler,
                journal, config, disruptor, socketListener, openRecords);

        // Step 3: setter injection to break circular deps (engine callbacks)
        tickRiskProcessor.setExitHandler(engine::handleExit);
        tickRiskProcessor.setStrategyEvaluator(se);
        scheduledPositionManager.setExitHandler(engine::handleExit);
        scheduledPositionManager.setStrategyEvaluator(se);
        se.setSignalHandler(engine::handleSignal);

        // Step 4: Disruptor handlers + OMS listener
        // TickStoreUpdater runs first (first-stage) so tick data is committed to the store
        // before TickRiskProcessor (second-stage) evaluates risk on that tick.
        disruptor.addFirstHandler(new TickStoreUpdater());
        disruptor.addHandler(tickRiskProcessor);
        orderTracker.addListener(engine);

        // Step 5: AnomalyDetector init + strategy loading
        ad.initialize(riskSessionState, scheduledPositionManager, tickStore, journal, riskConfig);
        cs.setOptionChainService(optionChainService);
        engine.loadYamlStrategies(cs, se);
        engine.initializePluggableStrategies(se);

        log.info("TradingEngine created — mode={} symbols={}",
                mode, String.join(",", config.getActiveSymbols()));
        return engine;
    }

    @Bean
    @Primary
    public TickDisruptorEngine tickDisruptorEngine(TradingEngine tradingEngine) {
        return tradingEngine.getDisruptorEngine();
    }

    @Bean
    @Primary
    public FyersSocketListener fyersSocketListener(TradingEngine tradingEngine) {
        return tradingEngine.getSocketListener();
    }

    @Bean
    public DimensionDataCache dimensionDataCache() {
        return DimensionDataCache.load(Path.of("data/dim"));
    }

    @Bean
    public SymbolMasterCache symbolMasterCache() {
        return SymbolMasterCache.load(Path.of("data/symbol_master"));
    }

    @Bean
    public CandleDatabase candleDatabase() {
        return new CandleDatabase(Path.of("data/history"));
    }

    @Bean
    public SymbolProfiler symbolProfiler(CandleDatabase candleDatabase) {
        return new SymbolProfiler(candleDatabase);
    }

    @Bean
    @Primary
    public BrokerCircuitBreaker brokerCircuitBreaker(TradingEngine tradingEngine) {
        return tradingEngine.getCircuitBreaker();
    }

    @Bean
    public CandleDownloader candleDownloader(
            IMarketDataAdapter marketDataAdapter,
            CandleDatabase candleDatabase,
            BrokerCircuitBreaker circuitBreaker) {
        return new CandleDownloader(marketDataAdapter, candleDatabase, 500, circuitBreaker);
    }

    @Bean
    public DownloadTracker downloadTracker(CandleDownloader candleDownloader) {
        return new DownloadTracker(candleDownloader);
    }

    @Bean
    public OptionChainService optionChainService(
            IMarketDataAdapter marketDataAdapter,
            OptionChainConfig optionChainConfig,
            SymbolMasterCache symbolMasterCache,
            ObjectMapper objectMapper) {
        return new OptionChainService(marketDataAdapter, optionChainConfig,
                symbolMasterCache, objectMapper);
    }
}
