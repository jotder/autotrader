package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnomalyDetectorTest {

    private RiskManager riskManager;
    private PositionMonitor positionMonitor;
    private TickStore tickStore;
    private TradeJournal journal;
    private RiskConfig riskConfig;
    private AnomalyDetector detector;
    private final AtomicInteger closedCount = new AtomicInteger(0);

    @BeforeEach
    void setup() {
        Map<String, String> env = new HashMap<>();
        env.put("RISK_INITIAL_CAPITAL_INR", "100000");
        env.put("RISK_MAX_DAILY_LOSS_INR", "5000");
        riskConfig = RiskConfig.fromEnvironment(env::get);

        riskManager = new RiskManager(riskConfig);
        
        positionMonitor = new PositionMonitor(null, riskConfig, (p, r) -> {}, null) {
            @Override
            public int closeAllPositions(ExitReason reason) {
                return closedCount.get();
            }
            @Override
            public void start() {}
            @Override
            public void stop() {}
        };
        
        tickStore = TickStore.getInstance();
        journal = new TradeJournal(ExecutionMode.BACKTEST);

        detector = new AnomalyDetector();
        detector.initialize(riskManager, positionMonitor, tickStore, journal, riskConfig);
    }

    @Test
    void triggerOnDrawdown() {
        // Use a real TradeRecord instead of mocking
        TradeRecord record = new TradeRecord(
                "corr-1", "NSE:SBIN-EQ", "test", ExecutionMode.PAPER,
                Signal.BUY, 500.0, 10, 490.0, 520.0,
                Instant.now(), 2.0, 1.0, Map.of());
        
        // Close it with a loss of 6000
        record.close(record.getEntryPrice() - (6000.0 / record.getQuantity()), Instant.now(), PositionMonitor.ExitReason.STOP_LOSS);
        
        riskManager.recordClosedTrade(record);
        
        closedCount.set(3);
        detector.check();
        
        assertTrue(detector.isTriggered());
        assertTrue(riskManager.isAnomalyMode());
        assertTrue(riskManager.getAnomalyReason().contains("CRITICAL DRAWDOWN"));
    }

    @Test
    void triggerOnBrokerErrors() {
        for (int i = 0; i < 10; i++) {
            detector.recordBrokerError();
        }
        
        detector.check();
        
        assertTrue(detector.isTriggered());
        assertTrue(riskManager.isAnomalyMode());
        assertTrue(riskManager.getAnomalyReason().contains("BROKER ERROR CASCADE"));
    }

    @Test
    void resetClearsTrigger() {
        for (int i = 0; i < 10; i++) detector.recordBrokerError();
        
        detector.check();
        assertTrue(detector.isTriggered());
        
        detector.reset();
        assertFalse(detector.isTriggered());
    }
}
