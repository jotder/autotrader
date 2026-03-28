package com.rj.engine;

import com.lmax.disruptor.EventHandler;
import com.rj.engine.disruptor.TickEvent;
import com.rj.config.RiskConfig;
import com.rj.model.OpenPosition;
import com.rj.model.Signal;
import com.rj.model.Tick;
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
 * Thread 4 — Position Monitor & Risk Tick Processor.
 *
 * <p>Operates in two modes:</p>
 * <ol>
 *   <li><b>Real-time (Disruptor):</b> Processes every tick against open positions for sub-ms SL/TP exit.</li>
 *   <li><b>Scheduled (1s):</b> Handles time-based force square-off at market close.</li>
 * </ol>
 */
public class PositionMonitor implements EventHandler<TickEvent> {

    private static final Logger log = LoggerFactory.getLogger(PositionMonitor.class);
    private final TickStore tickStore;

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final RiskConfig riskConfig;
    private final RiskManager riskManager;
    private final BiConsumer<OpenPosition, ExitReason> exitHandler;
    /** Active positions keyed by correlationId. */
    private final ConcurrentHashMap<String, OpenPosition> positions = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ── State ─────────────────────────────────────────────────────────────────
    private volatile StrategyEvaluator strategyEvaluator; 
    private ScheduledExecutorService scheduler;

    public PositionMonitor(TickStore tickStore,
                           RiskConfig riskConfig,
                           RiskManager riskManager,
                           BiConsumer<OpenPosition, ExitReason> exitHandler,
                           StrategyEvaluator strategyEvaluator) {
        this.tickStore = tickStore;
        this.riskConfig = riskConfig;
        this.riskManager = riskManager;
        this.exitHandler = exitHandler;
        this.strategyEvaluator = strategyEvaluator;
    }

    /**
     * HOT PATH: Called by Disruptor for every tick.
     */
    @Override
    public void onEvent(TickEvent event, long sequence, boolean endOfBatch) {
        if (positions.isEmpty()) return;
        
        // If kill switch active, don't process new ticks for exits (they should be closing anyway)
        if (riskManager.isKillSwitchActive() && !riskManager.isAnomalyMode()) return;

        Tick tick = event.getTick();
        if (tick == null) return;

        String symbol = tick.getSymbol();
        double currentPrice = tick.getLtp();

        for (OpenPosition pos : positions.values()) {
            if (pos.getSymbol().equals(symbol)) {
                checkRisk(pos, currentPrice);
            }
        }
    }

    private void checkRisk(OpenPosition pos, double price) {
        if (pos.isStopLossHit(price)) {
            log.info("[{}] Real-time SL hit: price={} sl={}", pos.getSymbol(), price, pos.getCurrentStopLoss());
            closePosition(pos, ExitReason.STOP_LOSS);
            return;
        }
        if (pos.isTakeProfitHit(price)) {
            log.info("[{}] Real-time TP hit: price={} tp={}", pos.getSymbol(), price, pos.getTakeProfit());
            closePosition(pos, ExitReason.TAKE_PROFIT);
            return;
        }
        updateTrailingStop(pos, price);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("PositionMonitor already running");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("position-time-monitor").factory());
        scheduler.scheduleAtFixedRate(this::scheduledRiskMaintenance, 1, 1, TimeUnit.SECONDS);
        log.info("PositionMonitor started (Real-time Disruptor + 1s Time monitor)");
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (scheduler != null) scheduler.shutdownNow();
        log.info("PositionMonitor stopped. {} positions still open", positions.size());
    }

    public void addPosition(OpenPosition position) {
        positions.put(position.getCorrelationId(), position);
        log.info("[{}] Position added to monitor: {}", position.getSymbol(), position);
    }

    public OpenPosition removePosition(String correlationId) {
        OpenPosition removed = positions.remove(correlationId);
        if (removed != null) {
            log.info("[{}] Position removed from monitor: {}", removed.getSymbol(), correlationId);
        }
        return removed;
    }

    public boolean hasOpenPosition(String symbol) {
        return positions.values().stream().anyMatch(p -> p.getSymbol().equals(symbol));
    }

    public Collection<OpenPosition> openPositions() {
        return Collections.unmodifiableCollection(positions.values());
    }

    public void setStrategyEvaluator(StrategyEvaluator se) {
        this.strategyEvaluator = se;
    }

    public int openPositionCount() {
        return positions.size();
    }

    public boolean isRunning() {
        return running.get();
    }

    public void requestManualExit(String correlationId) {
        OpenPosition pos = positions.get(correlationId);
        if (pos == null) {
            throw new IllegalArgumentException("No open position with correlationId: " + correlationId);
        }
        closePosition(pos, ExitReason.MANUAL);
    }

    private void scheduledRiskMaintenance() {
        if (positions.isEmpty()) {
            riskManager.updateCurrentEquity(0);
            return;
        }

        // 1. Update Open PnL & Check Drawdown
        double totalOpenPnL = 0;
        for (OpenPosition pos : positions.values()) {
            Tick lastTick = tickStore.getLastTick(pos.getSymbol());
            if (lastTick != null) {
                totalOpenPnL += pos.unrealizedPnl(lastTick.getLtp());
            }
        }
        riskManager.updateCurrentEquity(totalOpenPnL);

        // 2. Check for Anomaly Flatten (e.g. Drawdown breach triggered kill switch)
        if (riskManager.isAnomalyMode() && !positions.isEmpty()) {
            log.warn("Anomaly detected — Auto-flattening {} positions", positions.size());
            closeAllPositions(ExitReason.ANOMALY_FLATTEN);
            return;
        }

        // 3. Time-based exits
        ZonedDateTime now = ZonedDateTime.now(riskConfig.getExchangeZone());
        if (now.toLocalTime().compareTo(riskConfig.getMarketCloseTime()) >= 0) {
            log.warn("Market close reached — forcing square-off of {} positions", positions.size());
            closeAllPositions(ExitReason.FORCE_SQUAREOFF);
        }
    }

    private void updateTrailingStop(OpenPosition pos, double price) {
        double pnlPct = pos.getDirection() == Signal.BUY
                ? (price - pos.getEntryPrice()) / pos.getEntryPrice()
                : (pos.getEntryPrice() - price) / pos.getEntryPrice();

        if (!pos.isTrailingActivated() && pnlPct >= riskConfig.getTrailingActivationPercent()) {
            pos.setTrailingActivated(true);
            log.info("[{}] Trailing stop activated at price={}", pos.getSymbol(), price);
        }

        if (!pos.isTrailingActivated()) return;

        pos.updateHighWaterMark(price);
        double stepPct = riskConfig.getTrailingStepPercent();
        double newStop = pos.getDirection() == Signal.BUY
                ? pos.getHighWaterMark() * (1.0 - stepPct)
                : pos.getHighWaterMark() * (1.0 + stepPct);

        if (pos.stepTrailingStop(newStop)) {
            log.info("[{}] Trailing stop moved to {}", pos.getSymbol(), pos.getCurrentStopLoss());
            if (pos.isStopLossHit(price)) {
                closePosition(pos, ExitReason.TRAILING_STOP);
            }
        }
    }

    private void closePosition(OpenPosition pos, ExitReason reason) {
        positions.remove(pos.getCorrelationId());
        log.info("[{}] Closing position reason={}: {}", pos.getSymbol(), reason, pos);
        try {
            exitHandler.accept(pos, reason);
        } catch (Exception e) {
            log.error("[{}] Exit handler failed: {}", pos.getSymbol(), e.getMessage());
        }
        if (strategyEvaluator != null) {
            strategyEvaluator.onPositionClosed(pos.getSymbol());
        }
    }

    public int closeAllPositions(ExitReason reason) {
        int count = 0;
        for (OpenPosition pos : positions.values()) {
            closePosition(pos, reason);
            count++;
        }
        return count;
    }

    public enum ExitReason {
        STOP_LOSS, TAKE_PROFIT, TRAILING_STOP, FORCE_SQUAREOFF, MANUAL, ANOMALY_FLATTEN
    }
}
