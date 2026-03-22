package com.rj.config;

import com.rj.engine.TradingEngine;
import com.rj.model.TickStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bridges existing singletons and factory-created objects into the Spring bean context.
 * No engine code is modified — this class simply registers references so that
 * controllers and other Spring components can inject them.
 */
@Configuration
public class EngineConfiguration {

    @Bean
    public ConfigManager configManager() {
        return ConfigManager.getInstance();
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
    public TradingEngine tradingEngine() {
        return TradingEngine.create();
    }
}
