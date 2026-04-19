package com.rj.cli;

import com.rj.broker.IMarketDataAdapter;
import com.rj.config.ConfigManager;
import com.rj.config.StrategyYamlConfig;
import com.rj.config.SymbolRegistry;
import com.rj.config.YamlStrategyLoader;
import com.rj.engine.CandleDatabase;
import com.rj.fyers.FyersBrokerAdapter;

import java.nio.file.Path;
import java.util.Map;

/**
 * Bootstraps the runtime objects needed by the CLI drivers
 * ({@code DownloadScriptDataTest}, {@code RunStrategySignalsTest}).
 *
 * <p>This is a manual (non-Spring) wiring helper. It constructs a
 * {@link ConfigManager} directly, calls its {@code @PostConstruct} entry
 * explicitly, and builds the adapters/caches the drivers need.
 *
 * <p>Not thread-safe. One instance per CLI run.
 */
public final class CliContext {

    public final ConfigManager config;
    public final SymbolRegistry symbols;
    public final CandleDatabase candleDb;
    public final IMarketDataAdapter marketData;
    public final Map<String, StrategyYamlConfig> strategies;

    private CliContext(ConfigManager cfg, SymbolRegistry sym, CandleDatabase db,
                       IMarketDataAdapter md, Map<String, StrategyYamlConfig> strats) {
        this.config = cfg;
        this.symbols = sym;
        this.candleDb = db;
        this.marketData = md;
        this.strategies = strats;
    }

    /** Wire the CLI runtime from the repo working directory. */
    public static CliContext bootstrap() {
        ConfigManager config = new ConfigManager();
        config.loadConfiguration();   // @PostConstruct doesn't fire outside Spring

        SymbolRegistry symbols = config.getSymbolRegistry();
        if (symbols == null) {
            throw new IllegalStateException(
                    "SymbolRegistry not loaded; check that config/symbols.yaml exists");
        }

        CandleDatabase candleDb = new CandleDatabase(Path.of("data", "history"));

        IMarketDataAdapter marketData = new FyersBrokerAdapter(config);

        String stratFile = System.getProperty("strategy.file", "config/strategies/intraday.yaml");
        Map<String, StrategyYamlConfig> strategies =
                new YamlStrategyLoader().load(Path.of(stratFile));

        return new CliContext(config, symbols, candleDb, marketData, strategies);
    }
}
