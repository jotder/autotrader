package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.TickStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Detects anomalous conditions and triggers emergency flatten.
 * <p>
 * Monitored by {@link HealthMonitor} on its 60-second cycle.
 * When an anomaly is detected:
 * <ol>
 *   <li>Triggers {@link RiskManager#triggerAnomaly(String)}</li>
 *   <li>Calls {@link PositionMonitor#closeAllPositions} to flatten</li>
 *   <li>Logs to {@link TradeJournal}</li>
 * </ol>
 *
 * <h3>Anomaly conditions</h3>
 * <ul>
 *   <li>Rapid drawdown: intraday loss exceeds threshold (% of capital)</li>
 *   <li>Consecutive broker errors exceeds threshold</li>
 *   <li>Tick feed staleness exceeds threshold during market hours</li>
 *   <li>JVM heap usage exceeds critical threshold</li>
 * </ul>
 */
public class AnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetector.class);

    // ── Configurable thresholds ─────────────────────────────────────────────
    private final double maxDrawdownPct;           // e.g. 5.0 = 5% of capital
    private final int maxConsecutiveBrokerErrors;   // e.g. 10
    private final Duration maxFeedStaleness;        // e.g. 120 seconds
    private final double heapCriticalPct;           // e.g. 0.95

    // ── Dependencies ────────────────────────────────────────────────────────
    private final RiskManager riskManager;
    private final PositionMonitor positionMonitor;
    private final TickStore tickStore;
    private final TradeJournal journal;
    private final RiskConfig riskConfig;

    // ── State ───────────────────────────────────────────────────────────────
    private final AtomicInteger consecutiveBrokerErrors = new AtomicInteger(0);
    private final MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
    private volatile boolean triggered = false;

    public AnomalyDetector(RiskManager riskManager, PositionMonitor positionMonitor,
                           TickStore tickStore, TradeJournal journal, RiskConfig riskConfig) {
        this(riskManager, positionMonitor, tickStore, journal, riskConfig,
                5.0, 10, Duration.ofSeconds(120), 0.95);
    }

    public AnomalyDetector(RiskManager riskManager, PositionMonitor positionMonitor,
                           TickStore tickStore, TradeJournal journal, RiskConfig riskConfig,
                           double maxDrawdownPct, int maxConsecutiveBrokerErrors,
                           Duration maxFeedStaleness, double heapCriticalPct) {
        this.riskManager = riskManager;
        this.positionMonitor = positionMonitor;
        this.tickStore = tickStore;
        this.journal = journal;
        this.riskConfig = riskConfig;
        this.maxDrawdownPct = maxDrawdownPct;
        this.maxConsecutiveBrokerErrors = maxConsecutiveBrokerErrors;
        this.maxFeedStaleness = maxFeedStaleness;
        this.heapCriticalPct = heapCriticalPct;
    }

    // ── Check (called by HealthMonitor every 60s) ───────────────────────────

    /**
     * Run all anomaly checks. If any condition is met, triggers emergency flatten.
     *
     * @return the anomaly reason if triggered, or null if all clear
     */
    public String check() {
        if (triggered || riskManager.isAnomalyMode()) return null; // already triggered

        String reason;

        // 1. Rapid drawdown
        reason = checkDrawdown();
        if (reason != null) return trigger(reason);

        // 2. Broker error cascade
        reason = checkBrokerErrors();
        if (reason != null) return trigger(reason);

        // 3. Feed staleness
        reason = checkFeedStaleness();
        if (reason != null) return trigger(reason);

        // 4. Heap exhaustion
        reason = checkHeap();
        if (reason != null) return trigger(reason);

        return null;
    }

    // ── Individual checks ───────────────────────────────────────────────────

    private String checkDrawdown() {
        double capital = riskConfig.getInitialCapitalInr();
        if (capital <= 0) return null;
        double drawdownPct = Math.abs(riskManager.getDailyRealizedPnl()) / capital * 100.0;
        if (riskManager.getDailyRealizedPnl() < 0 && drawdownPct >= maxDrawdownPct) {
            return String.format("Rapid drawdown: %.1f%% of capital (threshold: %.1f%%)",
                    drawdownPct, maxDrawdownPct);
        }
        return null;
    }

    private String checkBrokerErrors() {
        int errors = consecutiveBrokerErrors.get();
        if (errors >= maxConsecutiveBrokerErrors) {
            return String.format("Broker error cascade: %d consecutive errors (threshold: %d)",
                    errors, maxConsecutiveBrokerErrors);
        }
        return null;
    }

    private String checkFeedStaleness() {
        // Only check during market hours
        var now = java.time.ZonedDateTime.now(riskConfig.getExchangeZone());
        var marketOpen = java.time.LocalTime.of(9, 15);
        var marketClose = java.time.LocalTime.of(15, 30);
        if (now.toLocalTime().isBefore(marketOpen) || now.toLocalTime().isAfter(marketClose)) {
            return null;
        }

        // Check if any symbol has recent ticks
        for (String symbol : tickStore.symbols()) {
            var buf = tickStore.bufferFor(symbol);
            if (buf != null && !buf.isEmpty()) {
                Instant newest = buf.newestTime();
                if (newest != null && Duration.between(newest, Instant.now()).compareTo(maxFeedStaleness) < 0) {
                    return null; // at least one symbol is fresh
                }
            }
        }

        // All symbols stale (only if we have subscribed symbols)
        if (tickStore.symbolCount() > 0) {
            return String.format("All tick feeds stale > %ds during market hours", maxFeedStaleness.getSeconds());
        }
        return null;
    }

    private String checkHeap() {
        long used = memBean.getHeapMemoryUsage().getUsed();
        long max = memBean.getHeapMemoryUsage().getMax();
        if (max > 0) {
            double pct = (double) used / max;
            if (pct >= heapCriticalPct) {
                return String.format("Heap exhaustion: %.0f%% used (threshold: %.0f%%)",
                        pct * 100, heapCriticalPct * 100);
            }
        }
        return null;
    }

    // ── Trigger ─────────────────────────────────────────────────────────────

    private String trigger(String reason) {
        triggered = true;
        log.error("ANOMALY DETECTED: {} — flattening all positions", reason);

        // 1. Trigger anomaly mode in RiskManager (blocks all new entries)
        riskManager.triggerAnomaly(reason);

        // 2. Close all open positions
        int closed = positionMonitor.closeAllPositions(PositionMonitor.ExitReason.ANOMALY_FLATTEN);
        log.error("ANOMALY FLATTEN: closed {} positions", closed);

        // 3. Audit log
        journal.log("ANOMALY_TRIGGERED", java.util.Map.of(
                "reason", reason,
                "positionsClosed", closed,
                "timestamp", Instant.now().toString(),
                "dailyPnl", String.format("%.2f", riskManager.getDailyRealizedPnl())));

        return reason;
    }

    // ── Broker error tracking (called by executor/API wrapper) ──────────────

    /** Record a broker API success — resets the consecutive error counter. */
    public void recordBrokerSuccess() {
        consecutiveBrokerErrors.set(0);
    }

    /** Record a broker API error — increments the consecutive error counter. */
    public void recordBrokerError() {
        int errors = consecutiveBrokerErrors.incrementAndGet();
        if (errors >= maxConsecutiveBrokerErrors / 2) {
            log.warn("Broker error count rising: {}/{}", errors, maxConsecutiveBrokerErrors);
        }
    }

    /** Reset state (e.g., after anomaly acknowledgement). */
    public void reset() {
        triggered = false;
        consecutiveBrokerErrors.set(0);
    }

    public boolean isTriggered() { return triggered; }
    public int getConsecutiveBrokerErrors() { return consecutiveBrokerErrors.get(); }
}
