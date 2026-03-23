package com.rj.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigManager implements IConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final String[] REQUIRED_KEYS = new String[]{
            "FYERS_APP_ID",
            "FYERS_SECRET_KEY",
            "FYERS_REDIRECT_URI",
            "FYERS_AUTH_CODE",
            "FYERS_SYMBOLS",
            "APP_ENV",
            "LOG_LEVEL"
    };
    private static volatile ConfigManager manager;
    private Dotenv dotenv;
    private boolean loaded;
    private String[] activeSymbols = new String[]{"NSE:NIFTY50-INDEX"};
    private Set<String> activeSymbolSet = new LinkedHashSet<>(Arrays.asList(activeSymbols));
    private RiskConfig riskConfig = RiskConfig.defaults();
    private StrategyConfig strategyConfig = StrategyConfig.defaults();

    private ConfigManager() {
        ensureLoaded();
    }

    public static ConfigManager getInstance() {
        if (manager == null) {
            synchronized (ConfigManager.class) {
                if (manager == null) {
                    manager = new ConfigManager();
                }
            }
        }
        return manager;
    }

    @Override
    public void loadConfiguration() {
        log.info("Loading system configuration from .env...");
        try {
            this.dotenv = Dotenv.configure().ignoreIfMissing().load();
            log.info("Configuration loaded. APP_ENV: {}", getProperty("APP_ENV"));

            String symbolsEnv = getProperty("FYERS_SYMBOLS");
            if (symbolsEnv != null && !symbolsEnv.isBlank()) {
                String[] parsedSymbols = Arrays.stream(symbolsEnv.split(","))
                        .map(String::trim)
                        .filter(symbol -> !symbol.isEmpty())
                        .toArray(String[]::new);
                if (parsedSymbols.length > 0) {
                    activeSymbols = parsedSymbols;
                }
            }

            activeSymbolSet = new LinkedHashSet<>(Arrays.asList(activeSymbols));
            riskConfig = RiskConfig.fromEnvironment(this::getProperty);
            strategyConfig = StrategyConfig.fromEnvironment(this::getProperty);
            loaded = true;
            log.info("Active symbols loaded: {}", String.join(", ", activeSymbols));
        } catch (Exception e) {
            log.error("Failed to load .env file", e);
        }
    }

    @Override
    public String getProperty(String key) {
        return dotenv != null ? dotenv.get(key) : null;
    }

    @Override
    public boolean validateRequiredConfiguration() {
        ensureLoaded();
        Set<String> missingKeys = new LinkedHashSet<>();
        for (String key : REQUIRED_KEYS) {
            String value = getProperty(key);
            if (value == null || value.isBlank()) {
                missingKeys.add(key);
            }
        }

        if (!missingKeys.isEmpty()) {
            log.error("Missing required configuration keys: {}", String.join(", ", missingKeys));
            log.error("Please update .env before starting the trading engine.");
            return false;
        }
        return true;
    }

    @Override
    public String[] getActiveSymbols() {
        ensureLoaded();
        return activeSymbols;
    }

    @Override
    public boolean isSymbolActive(String symbol) {
        ensureLoaded();
        return symbol != null && activeSymbolSet.contains(symbol.trim());
    }

    @Override
    public String getActiveStrategy(String symbol) {
        ensureLoaded();
        return getProperty("STRATEGY_DEFAULT") != null ? getProperty("STRATEGY_DEFAULT") : "ORB_15M";
    }

    @Override
    public RiskConfig getRiskConfig() {
        ensureLoaded();
        return riskConfig;
    }

    @Override
    public StrategyConfig getStrategyConfig() {
        ensureLoaded();
        return strategyConfig;
    }

    /**
     * Updates a single key in the .env file and reloads the in-memory dotenv.
     * If the key exists its value is replaced; otherwise a new line is appended.
     */
    public void updateEnvProperty(String key, String value) {
        Path envPath = Path.of(".env");
        try {
            List<String> lines;
            if (Files.exists(envPath)) {
                lines = Files.readAllLines(envPath);
            } else {
                lines = new java.util.ArrayList<>();
            }

            String prefix = key + "=";
            boolean found = false;
            List<String> updated = lines.stream().map(line -> {
                if (line.startsWith(prefix)) {
                    return prefix + value;
                }
                return line;
            }).collect(Collectors.toCollection(java.util.ArrayList::new));

            for (String line : updated) {
                if (line.startsWith(prefix)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                updated.add(prefix + value);
            }

            Files.write(envPath, updated);
            log.info("Updated {} in .env", key);

            // Reload dotenv so subsequent getProperty() calls see the new value
            this.dotenv = Dotenv.configure().ignoreIfMissing().load();
        } catch (IOException e) {
            log.error("Failed to update {} in .env: {}", key, e.getMessage());
        }
    }

    private void ensureLoaded() {
        if (!loaded) {
            loadConfiguration();
        }
    }
}
