package com.rj.config;

import com.rj.engine.TradingEngine;
import com.rj.fyers.FyersSocketListener;
import com.tts.in.websocket.FyersSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Starts the trading engine and socket listener after the Spring context is ready.
 */
@Component
public class EngineLifecycleManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EngineLifecycleManager.class);

    private final TradingEngine engine;
    private final FyersSocketListener socketListener;
    private final ConfigManager config;
    private volatile boolean running = false;

    public EngineLifecycleManager(TradingEngine engine, 
                                  FyersSocketListener socketListener,
                                  ConfigManager config) {
        this.engine = engine;
        this.socketListener = socketListener;
        this.config = config;
    }

    @Override
    public void start() {
        log.info("Starting PTA Backend via Spring lifecycle...");
        
        // Ensure socket is initialized (legacy Fyers SDK pattern)
        if (socketListener.socket == null) {
            socketListener.socket = new FyersSocket(30); // 30s timeout
            socketListener.fyersClass.clientId = config.getProperty("FYERS_APP_ID");
            socketListener.fyersClass.accessToken = config.getProperty("ACCESS_TOKEN");
        }

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
