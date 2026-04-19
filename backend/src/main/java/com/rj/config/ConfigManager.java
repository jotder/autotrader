package com.rj.config;

import com.tts.in.model.FyersClass;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class ConfigManager implements IConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final Path SYMBOLS_YAML_PATH = Path.of("config/symbols.yaml");
    private static final String[] REQUIRED_KEYS = {"FYERS_APP_ID", "FYERS_SECRET_KEY", "FYERS_REDIRECT_URI",
            "FYERS_AUTH_CODE", "APP_ENV", "LOG_LEVEL"};

    @Autowired
    private EnvConfigPersistence envConfigPersistence;

    private Dotenv dotenv;
    private String[] activeSymbols = {"NSE:NIFTY50-INDEX"};
    private Set<String> activeSymbolSet = new LinkedHashSet<>(Arrays.asList(activeSymbols));
    private RiskConfig riskConfig = RiskConfig.defaults();
    private StrategyConfig strategyConfig = StrategyConfig.defaults();
    private SymbolRegistry symbolRegistry;
//    private boolean cfgLoaded = false;

    @PostConstruct
    @Override
    public void loadConfiguration() {
        log.info("Loading system configuration from .env...");
        try {
            this.dotenv = Dotenv.configure().ignoreIfMissing().load();
            log.info("Configuration loaded. APP_ENV: {}", getProperty("APP_ENV"));

            if (Files.exists(SYMBOLS_YAML_PATH)) {
                symbolRegistry = SymbolRegistry.load(SYMBOLS_YAML_PATH);
                activeSymbols = symbolRegistry.allSymbols();
                activeSymbolSet = new LinkedHashSet<>(Arrays.asList(activeSymbols));
                log.info("Symbol registry loaded: {} symbols", symbolRegistry.size());
            }
            else {
                log.warn("config/symbols.yaml not found — falling back to .env FYERS_SYMBOLS (deprecated)");
                String symbolsEnv = getProperty("FYERS_SYMBOLS");
                if (symbolsEnv != null && !symbolsEnv.isBlank()) {
                    String[] parsed = Arrays.stream(symbolsEnv.split(","))
                            .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
                    if (parsed.length > 0) activeSymbols = parsed;
                }
                activeSymbolSet = new LinkedHashSet<>(Arrays.asList(activeSymbols));
            }

            riskConfig = RiskConfig.fromEnvironment(this::getProperty);
            strategyConfig = StrategyConfig.fromEnvironment(this::getProperty);
            log.info("Active symbols: {}", String.join(", ", activeSymbols));
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
        Set<String> missing = new LinkedHashSet<>();
        for (String key : REQUIRED_KEYS) {
            String v = getProperty(key);
            if (v == null || v.isBlank()) missing.add(key);
        }
        if (!missing.isEmpty()) {
            log.error("Missing required keys: {}", String.join(", ", missing));
            return false;
        }
        return true;
    }

    @Override
    public String[] getActiveSymbols() {
        return activeSymbols;
    }

    @Override
    public boolean isSymbolActive(String symbol) {
        if (symbolRegistry != null) return symbolRegistry.contains(symbol);
        return symbol != null && activeSymbolSet.contains(symbol.trim());
    }

    @Override
    public SymbolRegistry getSymbolRegistry() {
        return symbolRegistry;
    }

    @Override
    public String getActiveStrategy(String symbol) {
        String override = getProperty("STRATEGY_DEFAULT");
        return override != null ? override : "ORB_15M";
    }

    @Override
    public RiskConfig getRiskConfig() {
        return riskConfig;
    }

    @Override
    public StrategyConfig getStrategyConfig() {
        return strategyConfig;
    }

    /**
     * Updates a key in .env and reloads dotenv.
     * @deprecated Prefer injecting {@link EnvConfigPersistence} directly.
     */
    @Deprecated
    public void updateEnvProperty(String key, String value) {
        if (envConfigPersistence == null) envConfigPersistence = new EnvConfigPersistence();
        envConfigPersistence.update(key, value);
        this.dotenv = Dotenv.configure().ignoreIfMissing().load();
    }

    static FyersClass fyersClass;

    public FyersClass getFyersClass() {
        if (dotenv == null || fyersClass == null) {
            fyersClass = FyersClass.getInstance();
            loadConfiguration();
            fyersClass.clientId = getProperty("FYERS_APP_ID");
            fyersClass.accessToken = getProperty("ACCESS_TOKEN");
        }
        return fyersClass;
    }
}
