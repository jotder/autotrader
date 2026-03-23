package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.OpenPosition;
import com.rj.model.Signal;
import com.rj.model.TickBuffer;
import com.rj.model.TickStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Thread 4 — Position Monitor.
 *
 * <p>Runs every 1 second on a virtual-thread-backed scheduled executor.
 * For each open position it:</p>
 * <ol>
 *   <li>Reads the latest LTP from {@link TickStore}.</li>
 *   <li>Checks if stop-loss has been hit → triggers exit order.</li>
 *   <li>Checks if take-profit has been hit → triggers exit order.</li>
 *   <li>Updates the trailing stop (monotonic, activation-threshold guarded).</li>
 *   <li>Checks intraday square-off time → force-closes all positions at 15:15 IST.</li>
 * </ol>
 *
 * <p>Exit orders are dispatched via the {@code exitHandler} callback, which is
 * expected to call the broker OMS (live) or simulate a fill (paper).
 * The callback receives (position, exitReason).</p>
 */
public class PositionMonitor {

    private static final Logger log = LoggerFactory.getLogger(PositionMonitor.class);
    private final TickStore tickStore;

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final RiskConfig riskConfig;
    private final BiConsumer<OpenPosition, ExitReason> exitHandler;
    /** Active positions keyed by correlationId. */
    private final ConcurrentHashMap<String, OpenPosition> positions = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ── State ─────────────────────────────────────────────────────────────────
    private volatile StrategyEvaluator strategyEvaluator; // notified on close; settable post-construction
    private ScheduledExecutorService scheduler;
    public PositionMonitor(TickStore tickStore,
                           RiskConfig riskConfig,
                           BiConsumer<OpenPosition, ExitReason> exitHandler,
                           StrategyEvaluator strategyEvaluator) {
        this.tickStore = tickStore;
        this.riskConfig = riskConfig;
        this.exitHandler = exitHandler;
        this.strategyEvaluator = strategyEvaluator;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("PositionMonitor already running");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("position-monitor").factory());
        scheduler.scheduleAtFixedRate(this::monitorAll, 0, 1, TimeUnit.SECONDS);
        log.info("PositionMonitor started (1 s interval)");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (scheduler != null) scheduler.shutdownNow();
        log.info("PositionMonitor stopped. {} positions still open at shutdown", positions.size());
    }

    /**
     * Registers a new open position for monitoring.
     * Called by the OMS layer after a successful order fill.
     */
    public void addPosition(OpenPosition position) {
        positions.put(position.getCorrelationId(), position);
        log.info("[{}] Position added to monitor: {}", position.getSymbol(), position);
    }

    // ── Position registry ─────────────────────────────────────────────────────

    /** Returns true if there is at least one open position for the given symbol. */
    public boolean hasOpenPosition(String symbol) {
        return positions.values().stream().anyMatch(p -> p.getSymbol().equals(symbol));
    }

    /** Snapshot of all currently monitored positions (unmodifiable). */
    public Collection<OpenPosition> openPositions() {
        return Collections.unmodifiableCollection(positions.values());
    }

    /** Wire in StrategyEvaluator after construction to avoid circular dependency. */
    public void setStrategyEvaluator(StrategyEvaluator se) {
        this.strategyEvaluator = se;
    }

    public int openPositionCount() {
        return positions.size();
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Triggers a manual exit for the position with the given correlationId.
     *
     * @throws IllegalArgumentException if no open position matches the correlationId
     */
    public void requestManualExit(String correlationId) {
        OpenPosition pos = positions.get(correlationId);
        if (pos == null) {
            throw new IllegalArgumentException("No open position with correlationId: " + correlationId);
        }
        log.info("[{}] Manual exit requested for {}", pos.getSymbol(), correlationId);
        closePosition(pos, ExitReason.MANUAL);
    }

    private void monitorAll() {
        if (positions.isEmpty()) return;

        ZonedDateTime now = ZonedDateTime.now(riskConfig.getExchangeZone());

        for (OpenPosition pos : positions.values()) {
            try {
                monitorPosition(pos, now);
            } catch (Exception e) {
                log.error("[{}] Error monitoring position {}: {}",
                        pos.getSymbol(), pos.getCorrelationId(), e.getMessage(), e);
            }
        }
    }

    // ── Monitoring loop ───────────────────────────────────────────────────────

    private void monitorPosition(OpenPosition pos, ZonedDateTime now) {
        // ── Time-based force square-off ───────────────────────────────────────
        if (now.toLocalTime().compareTo(riskConfig.getMarketCloseTime()) >= 0) {
            log.warn("[{}] Force square-off at market close", pos.getSymbol());
            closePosition(pos, ExitReason.FORCE_SQUAREOFF);
            return;
        }

        // ── Get latest price ──────────────────────────────────────────────────
        double currentPrice = latestPrice(pos.getSymbol());
        if (currentPrice <= 0) {
            log.debug("[{}] No tick available yet — skipping monitor cycle", pos.getSymbol());
            return;
        }

        // ── Stop loss check ───────────────────────────────────────────────────
        if (pos.isStopLossHit(currentPrice)) {
            log.info("[{}] Stop loss hit: price={} sl={}", pos.getSymbol(),
                    String.format("%.2f", currentPrice),
                    String.format("%.2f", pos.getCurrentStopLoss()));
            closePosition(pos, ExitReason.STOP_LOSS);
            return;
        }

        // ── Take profit check ─────────────────────────────────────────────────
        if (pos.isTakeProfitHit(currentPrice)) {
            log.info("[{}] Take profit hit: price={} tp={}", pos.getSymbol(),
                    String.format("%.2f", currentPrice),
                    String.format("%.2f", pos.getTakeProfit()));
            closePosition(pos, ExitReason.TAKE_PROFIT);
            return;
        }

        // ── Trailing stop update ──────────────────────────────────────────────
        updateTrailingStop(pos, currentPrice);

        log.trace("[{}] Monitoring: price={} sl={} tp={} pnl={}",
                pos.getSymbol(),
                String.format("%.2f", currentPrice),
                String.format("%.2f", pos.getCurrentStopLoss()),
                String.format("%.2f", pos.getTakeProfit()),
                String.format("%.2f", pos.unrealizedPnl(currentPrice)));
    }

    private void updateTrailingStop(OpenPosition pos, double price) {
        double pnlPct = pos.getDirection() == Signal.BUY
                ? (price - pos.getEntryPrice()) / pos.getEntryPrice()
                : (pos.getEntryPrice() - price) / pos.getEntryPrice();

        // Activation: requires unrealized gain >= activation threshold
        if (!pos.isTrailingActivated()
                && pnlPct >= riskConfig.getTrailingActivationPercent()) {
            pos.setTrailingActivated(true);
            log.info("[{}] Trailing stop activated at price={}", pos.getSymbol(),
                    String.format("%.2f", price));
        }

        if (!pos.isTrailingActivated()) return;

        pos.updateHighWaterMark(price);
        double hwm = pos.getHighWaterMark();
        double stepPct = riskConfig.getTrailingStepPercent();

        double newStop = pos.getDirection() == Signal.BUY
                ? hwm * (1.0 - stepPct)
                : hwm * (1.0 + stepPct);

        if (pos.stepTrailingStop(newStop)) {
            log.info("[{}] Trailing stop moved to {}", pos.getSymbol(),
                    String.format("%.2f", pos.getCurrentStopLoss()));
            // Re-check: newly moved stop may already be hit
            if (pos.isStopLossHit(price)) {
                closePosition(pos, ExitReason.TRAILING_STOP);
            }
        }
    }

    // ── Trailing stop logic ───────────────────────────────────────────────────

    private void closePosition(OpenPosition pos, ExitReason reason) {
        positions.remove(pos.getCorrelationId());
        log.info("[{}] Closing position reason={}: {}", pos.getSymbol(), reason, pos);
        try {
            exitHandler.accept(pos, reason);
        } catch (Exception e) {
            log.error("[{}] Exit handler failed for {}: {}", pos.getSymbol(),
                    pos.getCorrelationId(), e.getMessage(), e);
        }
        if (strategyEvaluator != null) {
            strategyEvaluator.onPositionClosed(pos.getSymbol());
        }
    }

    // ── Position close ────────────────────────────────────────────────────────

    private double latestPrice(String symbol) {
        TickBuffer buf = tickStore.bufferFor(symbol);
        if (buf == null || buf.isEmpty()) return 0;
        // Snapshot the latest tick — we only need the last element
        var snapshot = buf.snapshot();
        return snapshot.isEmpty() ? 0 : snapshot.get(snapshot.size() - 1).getLtp();
    }

    // ── Tick data ─────────────────────────────────────────────────────────────

    /** Reasons an exit can be triggered. */
    public enum ExitReason {STOP_LOSS, TAKE_PROFIT, TRAILING_STOP, FORCE_SQUAREOFF, MANUAL}
}
