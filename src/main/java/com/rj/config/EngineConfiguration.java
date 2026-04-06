package com.rj.config;

import com.rj.engine.BrokerCircuitBreaker;
import com.rj.engine.CandleDatabase;
import com.rj.engine.CandleDownloader;
import com.rj.engine.DownloadTracker;
import com.rj.engine.SymbolProfiler;
import com.rj.engine.TradingEngine;
import com.rj.engine.disruptor.TickDisruptorEngine;
import com.rj.fyers.FyersDataApi;
import com.rj.fyers.FyersSocketListener;
import com.rj.model.TickStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Bridges existing singletons and factory-created objects into the Spring bean context.
 * No engine code is modified — this class simply registers references so that
 * controllers and other Spring components can inject them.
 */
@Configuration
public class EngineConfiguration {

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
    public SymbolRegistry symbolRegistry(ConfigManager configManager) {
        return configManager.getSymbolRegistry();
    }

    @Bean
    public TradingEngine tradingEngine(ConfigManager configManager) {
        return TradingEngine.create(configManager);
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
            CandleDatabase candleDatabase,
            BrokerCircuitBreaker circuitBreaker) {
        return new CandleDownloader(
                new FyersDataApi(), candleDatabase, 500, circuitBreaker);
    }

    @Bean
    public DownloadTracker downloadTracker(CandleDownloader candleDownloader) {
        return new DownloadTracker(candleDownloader);
    }
}
