package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.ExecutionMode;
import com.rj.model.TickStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AnomalyDetectorTest {

    private RiskConfig riskConfig;
    private RiskManager riskManager;
    private PositionMonitor positionMonitor;
    private TradeJournal journal;
    private AnomalyDetector detector;

    @BeforeEach
    void setup() {
        riskConfig = createTestRiskConfig();
        riskManager = new RiskManager(riskConfig);
        // PositionMonitor needs TickStore, RiskConfig, and exit handler
        positionMonitor = new PositionMonitor(
                TickStore.getInstance(), riskConfig, (pos, reason) -> {}, null);
        journal = new TradeJournal(ExecutionMode.BACKTEST);
        detector = new AnomalyDetector(riskManager, positionMonitor,
                TickStore.getInstance(), journal, riskConfig,
                5.0,   // 5% max drawdown
                3,     // 3 consecutive broker errors
                Duration.ofSeconds(120),
                0.99); // 99% heap (effectively disabled for tests)
    }

    @Test
    void noAnomalyWhenAllClear() {
        assertNull(detector.check());
        assertFalse(detector.isTriggered());
        assertFalse(riskManager.isAnomalyMode());
    }

    @Test
    void brokerErrorCascadeTriggersAnomaly() {
        // Record errors up to threshold
        detector.recordBrokerError();
        detector.recordBrokerError();
        assertNull(detector.check()); // not yet at threshold (3)

        detector.recordBrokerError();
        String reason = detector.check();
        assertNotNull(reason);
        assertTrue(reason.contains("Broker error cascade"));
        assertTrue(detector.isTriggered());
        assertTrue(riskManager.isAnomalyMode());
        assertTrue(riskManager.isKillSwitchActive());
    }

    @Test
    void brokerSuccessResetsErrorCounter() {
        detector.recordBrokerError();
        detector.recordBrokerError();
        assertEquals(2, detector.getConsecutiveBrokerErrors());

        detector.recordBrokerSuccess();
        assertEquals(0, detector.getConsecutiveBrokerErrors());
    }

    @Test
    void drawdownTriggersAnomaly() {
        // Simulate 5% drawdown via multiple losing trades
        // Capital=100000, threshold=5%, need > 5000 INR loss
        // Create small losing trades that accumulate
        for (int i = 0; i < 6; i++) {
            simulateSmallLoss(riskManager, 1000); // 6 × 1000 = 6000 > 5000
        }

        String reason = detector.check();
        assertNotNull(reason);
        assertTrue(reason.contains("drawdown"));
        assertTrue(riskManager.isAnomalyMode());
    }

    @Test
    void alreadyTriggeredDoesNotRetrigger() {
        detector.recordBrokerError();
        detector.recordBrokerError();
        detector.recordBrokerError();

        String first = detector.check();
        assertNotNull(first);

        // Second check should return null (already triggered)
        assertNull(detector.check());
    }

    @Test
    void resetClearsState() {
        detector.recordBrokerError();
        detector.recordBrokerError();
        detector.recordBrokerError();
        detector.check(); // triggers

        assertTrue(detector.isTriggered());

        detector.reset();
        assertFalse(detector.isTriggered());
        assertEquals(0, detector.getConsecutiveBrokerErrors());
    }

    @Test
    void anomalyModeBlocksResetDay() {
        riskManager.triggerAnomaly("test");
        assertTrue(riskManager.isAnomalyMode());
        assertTrue(riskManager.isKillSwitchActive());

        // resetDay should be blocked
        riskManager.resetDay();
        assertTrue(riskManager.isKillSwitchActive()); // still active

        // Acknowledge first
        assertTrue(riskManager.acknowledgeAnomaly());
        assertFalse(riskManager.isAnomalyMode());

        // Now resetDay works
        riskManager.resetDay();
        assertFalse(riskManager.isKillSwitchActive());
    }

    @Test
    void acknowledgeAnomalyWhenNotActive() {
        assertFalse(riskManager.acknowledgeAnomaly());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static int tradeSeq = 0;

    /**
     * Simulate a losing trade of the given INR amount.
     * Entry=1000, qty=1, exit = entry - lossAmount → pnl = -lossAmount.
     */
    private void simulateSmallLoss(RiskManager rm, double lossAmount) {
        tradeSeq++;
        double entry = 1000.0;
        int qty = 1;
        double sl = entry - lossAmount - 10; // SL below exit
        var trade = new com.rj.model.TradeRecord(
                "test-corr-" + tradeSeq, "NSE:TEST-EQ", "test-strategy",
                com.rj.model.ExecutionMode.PAPER, com.rj.model.Signal.BUY,
                entry, qty, sl, entry + 100,
                java.time.Instant.now(), 10.0, 0.8,
                java.util.Map.of(com.rj.model.Timeframe.M5, com.rj.model.Signal.BUY));
        // Close at a loss: exit = entry - lossAmount
        trade.close(entry - lossAmount, java.time.Instant.now(),
                PositionMonitor.ExitReason.STOP_LOSS);
        rm.recordClosedTrade(trade);
    }

    private static RiskConfig createTestRiskConfig() {
        return RiskConfig.fromEnvironment(key -> switch (key) {
            case "RISK_INITIAL_CAPITAL_INR" -> "100000";
            case "RISK_MAX_DAILY_LOSS_INR" -> "10000";
            case "RISK_MAX_DAILY_PROFIT_INR" -> "20000";
            case "RISK_MAX_PER_TRADE_PCT" -> "0.02";
            case "RISK_MAX_QTY_PER_ORDER" -> "100";
            case "RISK_MAX_CONSECUTIVE_LOSSES" -> "5";
            case "RISK_NO_NEW_TRADES_AFTER" -> "15:00";
            case "RISK_MARKET_CLOSE_TIME" -> "15:15";
            case "RISK_TRAILING_ACTIVATION_PCT" -> "0.015";
            case "RISK_TRAILING_STEP_PCT" -> "0.005";
            default -> null;
        });
    }
}
