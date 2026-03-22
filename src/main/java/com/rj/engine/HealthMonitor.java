package com.rj.engine;

import com.rj.model.TickStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread 5 — Health Monitor.
 *
 * <p>Runs every 60 seconds on a virtual-thread-backed executor.
 * Checks all service components and logs a health summary.
 * Emits WARN if any component is degraded; ERROR if critical thresholds are crossed.</p>
 *
 * <h3>Checks performed</h3>
 * <ul>
 *   <li><b>Tick freshness</b> — last tick age per symbol must be &lt; 30 s during market hours.</li>
 *   <li><b>Candle workers</b> — all expected workers must be alive.</li>
 *   <li><b>Strategy evaluator</b> — queue must not be backing up.</li>
 *   <li><b>Position monitor</b> — must be running if positions are open.</li>
 *   <li><b>JVM heap</b> — WARN at 80 %, ERROR at 90 %.</li>
 * </ul>
 */
public class HealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(HealthMonitor.class);

    private static final Duration MAX_TICK_STALENESS  = Duration.ofSeconds(30);
    private static final int      MAX_QUEUE_DEPTH     = 500;
    private static final double   HEAP_WARN_THRESHOLD = 0.80;
    private static final double   HEAP_CRIT_THRESHOLD = 0.90;

    private final TickStore          tickStore;
    private final CandleService      candleService;
    private final StrategyEvaluator  strategyEvaluator;
    private final PositionMonitor    positionMonitor;
    private final String[]           activeSymbols;

    private final AtomicBoolean            running   = new AtomicBoolean(false);
    private       ScheduledExecutorService scheduler;
    private final MemoryMXBean             memBean   = ManagementFactory.getMemoryMXBean();

    public HealthMonitor(TickStore tickStore,
                         CandleService candleService,
                         StrategyEvaluator strategyEvaluator,
                         PositionMonitor positionMonitor,
                         String[] activeSymbols) {
        this.tickStore         = tickStore;
        this.candleService     = candleService;
        this.strategyEvaluator = strategyEvaluator;
        this.positionMonitor   = positionMonitor;
        this.activeSymbols     = activeSymbols;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("HealthMonitor already running");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("health-monitor").factory());
        scheduler.scheduleAtFixedRate(this::runChecks, 10, 60, TimeUnit.SECONDS);
        log.info("HealthMonitor started (60 s interval)");
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (scheduler != null) scheduler.shutdownNow();
        log.info("HealthMonitor stopped");
    }

    public boolean isRunning() { return running.get(); }

    // ── Health check cycle ────────────────────────────────────────────────────

    private void runChecks() {
        log.info("=== Health Check ===");
        checkTickFreshness();
        checkCandleWorkers();
        checkStrategyEvaluator();
        checkPositionMonitor();
        checkHeapUsage();
        log.info("=== Health Check Complete ===");
    }

    // ── Individual checks ─────────────────────────────────────────────────────

    private void checkTickFreshness() {
        Instant now = Instant.now();
        int stale = 0;
        for (String symbol : activeSymbols) {
            var buf = tickStore.bufferFor(symbol);
            if (buf == null || buf.isEmpty()) {
                log.warn("[HealthMonitor] TICK [{}] No buffer/ticks yet", symbol);
                stale++;
                continue;
            }
            Duration age = Duration.between(buf.newestTime(), now);
            if (age.compareTo(MAX_TICK_STALENESS) > 0) {
                log.warn("[HealthMonitor] TICK [{}] Stale — last tick {}s ago", symbol, age.toSeconds());
                stale++;
            } else {
                log.info("[HealthMonitor] TICK [{}] OK — last tick {}s ago, buffer size={}",
                        symbol, age.toSeconds(), buf.size());
            }
        }
        if (stale == 0) {
            log.info("[HealthMonitor] TICK All {} symbols fresh", activeSymbols.length);
        }
    }

    private void checkCandleWorkers() {
        int expected = activeSymbols.length * CandleService.DEFAULT_TIMEFRAMES.length;
        int actual   = candleService.workerCount();
        if (actual < expected) {
            log.error("[HealthMonitor] CANDLE Workers: {}/{} — some workers have died",
                    actual, expected);
        } else {
            log.info("[HealthMonitor] CANDLE Workers: {}/{} OK", actual, expected);
        }
        log.info("[HealthMonitor] CANDLE Service running: {}", candleService.isRunning());
    }

    private void checkStrategyEvaluator() {
        boolean alive = strategyEvaluator.isRunning();
        int     depth = strategyEvaluator.queueDepth();
        if (!alive) {
            log.error("[HealthMonitor] STRATEGY Evaluator is NOT running!");
        } else if (depth > MAX_QUEUE_DEPTH) {
            log.warn("[HealthMonitor] STRATEGY Queue depth {} exceeds threshold {}",
                    depth, MAX_QUEUE_DEPTH);
        } else {
            log.info("[HealthMonitor] STRATEGY OK — running={} queueDepth={}", alive, depth);
        }
    }

    private void checkPositionMonitor() {
        boolean alive     = positionMonitor.isRunning();
        int     openCount = positionMonitor.openPositionCount();
        if (!alive) {
            log.error("[HealthMonitor] POSITIONS Monitor is NOT running! {} positions unmonitored",
                    openCount);
        } else {
            log.info("[HealthMonitor] POSITIONS Monitor running — {} open positions", openCount);
            if (openCount > 0) {
                positionMonitor.openPositions().forEach(pos ->
                        log.info("[HealthMonitor]   → {}", pos));
            }
        }
    }

    private void checkHeapUsage() {
        long used  = memBean.getHeapMemoryUsage().getUsed();
        long max   = memBean.getHeapMemoryUsage().getMax();
        if (max <= 0) return;
        double ratio = (double) used / max;
        String pct   = String.format("%.1f%%", ratio * 100);
        if (ratio >= HEAP_CRIT_THRESHOLD) {
            log.error("[HealthMonitor] HEAP CRITICAL: {} used={} MB max={} MB",
                    pct, used / 1_048_576, max / 1_048_576);
        } else if (ratio >= HEAP_WARN_THRESHOLD) {
            log.warn("[HealthMonitor] HEAP WARN: {} used={} MB max={} MB",
                    pct, used / 1_048_576, max / 1_048_576);
        } else {
            log.info("[HealthMonitor] HEAP OK: {} used={} MB max={} MB",
                    pct, used / 1_048_576, max / 1_048_576);
        }
    }
}
