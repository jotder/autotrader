package com.rj.config;

public interface IConfiguration {
    void loadConfiguration();

    boolean validateRequiredConfiguration();

    String getProperty(String key);

    String[] getActiveSymbols();

    boolean isSymbolActive(String symbol);

    String getActiveStrategy(String symbol);

    RiskConfig getRiskConfig();

    StrategyConfig getStrategyConfig();
}
