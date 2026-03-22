package com.rj.config;

import com.rj.engine.TradingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Starts the trading engine after the Spring context (and embedded Tomcat) is fully ready,
 * and stops it cleanly before the context closes.
 *
 * <p>Phase {@code Integer.MAX_VALUE} means: start last, stop first — so the REST API is
 * available before the engine starts, and the engine stops before Tomcat unbinds.</p>
 *
 * <p>The engine's own JVM shutdown hook is harmless: its {@code AtomicBoolean} guard
 * makes double-stop a no-op.</p>
 */
@Component
public class EngineLifecycleManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EngineLifecycleManager.class);

    private final TradingEngine engine;
    private volatile boolean running = false;

    public EngineLifecycleManager(TradingEngine engine) {
        this.engine = engine;
    }

    @Override
    public void start() {
        log.info("Starting TradingEngine via Spring lifecycle...");
        engine.start();
        running = true;
    }

    @Override
    public void stop() {
        log.info("Stopping TradingEngine via Spring lifecycle...");
        engine.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
