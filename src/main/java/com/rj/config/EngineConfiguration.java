package com.rj.config;

import com.rj.broker.IMarketDataAdapter;
import com.rj.broker.IOrderAdapter;
import com.rj.engine.BrokerCircuitBreaker;
import com.rj.engine.CandleDatabase;
import com.rj.engine.CandleDownloader;
import com.rj.engine.DownloadTracker;
import com.rj.engine.SymbolProfiler;
import com.rj.engine.TradingEngine;
import com.rj.engine.disruptor.TickDisruptorEngine;
import com.rj.fyers.FyersSocketListener;
import com.rj.model.TickStore;
import com.tts.in.model.FyersClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class EngineConfiguration {

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
    public TradingEngine tradingEngine(ConfigManager configManager, IOrderAdapter orderAdapter) {
        return TradingEngine.create(configManager, orderAdapter);
    }

    @Bean
    public TickDisruptorEngine tickDisruptorEngine(TradingEngine tradingEngine) {
        return tradingEngine.getDisruptorEngine();
    }

    @Bean
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
}
