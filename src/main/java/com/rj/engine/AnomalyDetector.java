package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.TickStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * F-28 — Anomaly Auto-Protection.
 *
 * <p>High-frequency detector that monitors system stress and capital risk.
 * Runs on a 1-second cycle using a Virtual Thread.</p>
 */
@Component
public class AnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetector.class);

    // ── Configurable thresholds ─────────────────────────────────────────────
    private final double maxDrawdownPct = 5.0;           // 5% of capital
    private final int maxConsecutiveBrokerErrors = 10;
    private final Duration maxFeedStaleness = Duration.ofSeconds(30);
    private final double heapCriticalPct = 0.90;

    // ── Dependencies ────────────────────────────────────────────────────────
    private RiskManager riskManager;
    private PositionMonitor positionMonitor;
    private TickStore tickStore;
    private TradeJournal journal;
    private RiskConfig riskConfig;

    // ── State ───────────────────────────────────────────────────────────────
    private final AtomicInteger consecutiveBrokerErrors = new AtomicInteger(0);
    private final MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
    private final AtomicBoolean triggered = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    public AnomalyDetector() {
        // Required for Spring, but dependencies wired via TradingEngine for now
    }

    public void initialize(RiskManager riskManager, PositionMonitor positionMonitor,
                           TickStore tickStore, TradeJournal journal, RiskConfig riskConfig) {
        this.riskManager = riskManager;
        this.positionMonitor = positionMonitor;
        this.tickStore = tickStore;
        this.journal = journal;
        this.riskConfig = riskConfig;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        if (scheduler != null) return;
        
        log.info("Starting AnomalyDetector (1s protection cycle)...");
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("anomaly-guard").factory());
        
        scheduler.scheduleAtFixedRate(this::check, 5, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * Run all anomaly checks. If any condition is met, triggers emergency flatten.
     */
    public void check() {
        if (triggered.get() || riskManager == null || riskManager.isAnomalyMode()) return;

        String reason = null;

        // 1. Rapid drawdown
        reason = checkDrawdown();
        
        // 2. Broker error cascade
        if (reason == null) reason = checkBrokerErrors();

        // 3. Feed staleness (only during market hours)
        if (reason == null) reason = checkFeedStaleness();

        // 4. Heap exhaustion
        if (reason == null) reason = checkHeap();

        if (reason != null) {
            trigger(reason);
        }
    }

    // ── Individual checks ───────────────────────────────────────────────────

    private String checkDrawdown() {
        double capital = riskConfig.getInitialCapitalInr();
        if (capital <= 0) return null;
        
        double pnl = riskManager.getDailyRealizedPnl();
        double drawdownPct = Math.abs(pnl) / capital * 100.0;
        
        if (pnl < 0 && drawdownPct >= maxDrawdownPct) {
            return String.format("CRITICAL DRAWDOWN: %.1f%% of capital", drawdownPct);
        }
        return null;
    }

    private String checkBrokerErrors() {
        int errors = consecutiveBrokerErrors.get();
        if (errors >= maxConsecutiveBrokerErrors) {
            return String.format("BROKER ERROR CASCADE: %d consecutive failures", errors);
        }
        return null;
    }

    private String checkFeedStaleness() {
        var now = java.time.ZonedDateTime.now(riskConfig.getExchangeZone());
        var marketOpen = java.time.LocalTime.of(9, 15);
        var marketClose = java.time.LocalTime.of(15, 30);
        
        if (now.toLocalTime().isBefore(marketOpen) || now.toLocalTime().isAfter(marketClose)) {
            return null;
        }

        if (tickStore.symbolCount() == 0) return null;

        boolean allStale = true;
        for (String symbol : tickStore.symbols()) {
            var buf = tickStore.bufferFor(symbol);
            if (buf != null && !buf.isEmpty()) {
                Duration age = Duration.between(buf.newestTime(), Instant.now());
                if (age.compareTo(maxFeedStaleness) < 0) {
                    allStale = false;
                    break;
                }
            }
        }

        if (allStale) {
            return String.format("FEED STALENESS: All feeds stopped for >%ds", maxFeedStaleness.getSeconds());
        }
        return null;
    }

    private String checkHeap() {
        long used = memBean.getHeapMemoryUsage().getUsed();
        long max = memBean.getHeapMemoryUsage().getMax();
        if (max > 0 && (double) used / max >= heapCriticalPct) {
            return String.format("SYSTEM STRESS: JVM Heap usage at %.0f%%", (double) used / max * 100);
        }
        return null;
    }

    // ── Trigger ─────────────────────────────────────────────────────────────

    private void trigger(String reason) {
        if (!triggered.compareAndSet(false, true)) return;
        
        log.error("!!! EMERGENCY ANOMALY TRIGGERED !!! Reason: {}", reason);

        // 1. Block all new entries via RiskManager
        riskManager.triggerAnomaly(reason);

        // 2. Dispatch flatten operation to a dedicated virtual thread to avoid blocking the guard
        Thread.ofVirtual().name("emergency-flattener").start(() -> {
            try {
                int closed = positionMonitor.closeAllPositions(PositionMonitor.ExitReason.ANOMALY_FLATTEN);
                log.error("EMERGENCY FLATTEN COMPLETE: closed {} positions", closed);
                
                journal.log("ANOMALY_FLATTEN", java.util.Map.of(
                        "reason", reason,
                        "closedCount", closed,
                        "pnlAtTrigger", riskManager.getDailyRealizedPnl()
                ));
            } catch (Exception e) {
                log.error("CRITICAL: Emergency flatten failed: {}", e.getMessage(), e);
            }
        });
    }

    public void recordBrokerSuccess() { consecutiveBrokerErrors.set(0); }
    public void recordBrokerError() { consecutiveBrokerErrors.incrementAndGet(); }
    public void reset() { triggered.set(false); consecutiveBrokerErrors.set(0); }
    public boolean isTriggered() { return triggered.get(); }
    public int getConsecutiveBrokerErrors() { return consecutiveBrokerErrors.get(); }
}
