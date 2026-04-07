package com.rj.config;

import com.rj.engine.TradingEngine;
import com.rj.fyers.TokenRefreshScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Starts the trading engine and token refresh scheduler after the Spring context is ready.
 * Broker connection is user-triggered via POST /api/connect — not automatic on startup.
 */
@Component
public class EngineLifecycleManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EngineLifecycleManager.class);

    private final TradingEngine engine;
    private final TokenRefreshScheduler tokenRefreshScheduler;
    private volatile boolean running = false;

    public EngineLifecycleManager(TradingEngine engine, TokenRefreshScheduler tokenRefreshScheduler) {
        this.engine = engine;
        this.tokenRefreshScheduler = tokenRefreshScheduler;
    }

    @Override
    public void start() {
        log.info("Starting PTA Backend via Spring lifecycle...");
        engine.start();
        tokenRefreshScheduler.start();
        running = true;
    }

    @Override
    public void stop() {
        log.info("Stopping TradingEngine via Spring lifecycle...");
        tokenRefreshScheduler.stop();
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
