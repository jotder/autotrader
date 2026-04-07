package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.engine.risk.RiskSessionState;
import com.rj.model.OpenPosition;
import com.rj.model.Tick;
import com.rj.model.TickStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * 1-second scheduler for time-based exits, drawdown propagation, and manual exit control.
 * Not on the hot path — all operations run in the scheduler thread.
 */
public class ScheduledPositionManager {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPositionManager.class);

    private final PositionBook     positionBook;
    private final RiskSessionState riskSessionState;
    private final RiskConfig       riskConfig;
    private final TickStore        tickStore;

    private volatile BiConsumer<OpenPosition, ExitReason> exitHandler;
    private volatile StrategyEvaluator strategyEvaluator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    public ScheduledPositionManager(PositionBook positionBook,
                                    RiskSessionState riskSessionState,
                                    RiskConfig riskConfig,
                                    TickStore tickStore) {
        this.positionBook     = positionBook;
        this.riskSessionState = riskSessionState;
        this.riskConfig       = riskConfig;
        this.tickStore        = tickStore;
    }

    public void setExitHandler(BiConsumer<OpenPosition, ExitReason> exitHandler) {
        this.exitHandler = exitHandler;
    }

    public void setStrategyEvaluator(StrategyEvaluator strategyEvaluator) {
        this.strategyEvaluator = strategyEvaluator;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("ScheduledPositionManager already running");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("position-time-monitor").factory());
        scheduler.scheduleAtFixedRate(this::scheduledRiskMaintenance, 1, 1, TimeUnit.SECONDS);
        log.info("ScheduledPositionManager started (Real-time Disruptor + 1s Time monitor)");
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (scheduler != null) scheduler.shutdownNow();
        log.info("ScheduledPositionManager stopped. {} positions still open",
                positionBook.openPositionCount());
    }

    public boolean isRunning() {
        return running.get();
    }

    // ── Manual control ────────────────────────────────────────────────────────

    public void requestManualExit(String correlationId) {
        OpenPosition pos = positionBook.get(correlationId);
        if (pos == null) {
            throw new IllegalArgumentException("No open position with correlationId: " + correlationId);
        }
        closePosition(pos, ExitReason.MANUAL);
    }

    public int closeAllPositions(ExitReason reason) {
        int count = 0;
        for (OpenPosition pos : positionBook.openPositions()) {
            closePosition(pos, reason);
            count++;
        }
        return count;
    }

    // ── Scheduled maintenance ─────────────────────────────────────────────────

    private void scheduledRiskMaintenance() {
        if (positionBook.isEmpty()) {
            riskSessionState.updateCurrentEquity(0);
            return;
        }

        // 1. Compute total open PnL and propagate to risk state
        double totalOpenPnL = 0;
        if (tickStore != null) {
            for (OpenPosition pos : positionBook.openPositions()) {
                Tick lastTick = tickStore.getLastTick(pos.getSymbol());
                if (lastTick != null) totalOpenPnL += pos.unrealizedPnl(lastTick.getLtp());
            }
        }
        riskSessionState.updateCurrentEquity(totalOpenPnL);

        // 2. Anomaly flatten
        if (riskSessionState.isAnomalyMode() && !positionBook.isEmpty()) {
            log.warn("Anomaly detected — Auto-flattening {} positions", positionBook.openPositionCount());
            closeAllPositions(ExitReason.ANOMALY_FLATTEN);
            return;
        }

        // 3. Time-based force square-off
        ZonedDateTime now = ZonedDateTime.now(riskConfig.getExchangeZone());
        if (now.toLocalTime().compareTo(riskConfig.getMarketCloseTime()) >= 0) {
            log.warn("Market close reached — forcing square-off of {} positions",
                    positionBook.openPositionCount());
            closeAllPositions(ExitReason.FORCE_SQUAREOFF);
        }
    }

    private void closePosition(OpenPosition pos, ExitReason reason) {
        positionBook.remove(pos.getCorrelationId());
        log.info("[{}] Closing position reason={}: {}", pos.getSymbol(), reason, pos);
        BiConsumer<OpenPosition, ExitReason> handler = this.exitHandler;
        StrategyEvaluator se = this.strategyEvaluator;
        try {
            if (handler != null) handler.accept(pos, reason);
        } catch (Exception e) {
            log.error("[{}] Exit handler failed: {}", pos.getSymbol(), e.getMessage());
        }
        if (se != null) se.onPositionClosed(pos.getSymbol());
    }
}
