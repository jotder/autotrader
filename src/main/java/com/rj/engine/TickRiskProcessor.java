package com.rj.engine;

import com.lmax.disruptor.EventHandler;
import com.rj.config.RiskConfig;
import com.rj.engine.disruptor.TickEvent;
import com.rj.engine.risk.RiskSessionState;
import com.rj.model.OpenPosition;
import com.rj.model.Signal;
import com.rj.model.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * Disruptor hot-path EventHandler extracted from PositionMonitor.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Evaluate every tick against open positions for sub-ms SL/TP detection.</li>
 *   <li>Manage trailing stop updates on the hot path.</li>
 *   <li>Delegate close decisions to the registered exitHandler and notify StrategyEvaluator.</li>
 * </ul>
 *
 * <p>No blocking operations are permitted inside {@link #onEvent}.
 */
public class TickRiskProcessor implements EventHandler<TickEvent> {

    private static final Logger log = LoggerFactory.getLogger(TickRiskProcessor.class);

    private final PositionBook      positionBook;
    private final RiskSessionState  riskState;
    private final RiskConfig        riskConfig;

    private volatile BiConsumer<OpenPosition, ExitReason> exitHandler;
    private volatile StrategyEvaluator                    strategyEvaluator;

    // ── Constructor ───────────────────────────────────────────────────────────

    public TickRiskProcessor(PositionBook positionBook,
                             RiskSessionState riskState,
                             RiskConfig riskConfig) {
        this.positionBook = positionBook;
        this.riskState    = riskState;
        this.riskConfig   = riskConfig;
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setExitHandler(BiConsumer<OpenPosition, ExitReason> exitHandler) {
        this.exitHandler = exitHandler;
    }

    public void setStrategyEvaluator(StrategyEvaluator strategyEvaluator) {
        this.strategyEvaluator = strategyEvaluator;
    }

    // ── HOT PATH ──────────────────────────────────────────────────────────────

    /**
     * Called by the Disruptor for every tick — must be allocation-free and non-blocking.
     */
    @Override
    public void onEvent(TickEvent event, long sequence, boolean endOfBatch) {
        if (positionBook.isEmpty()) return;

        // When kill switch is active but we are NOT in anomaly-flatten mode,
        // skip tick processing — positions are already being closed elsewhere.
        if (riskState.isKillSwitchActive() && !riskState.isAnomalyMode()) return;

        Tick tick = event.getTick();
        if (tick == null) return;

        String symbol       = tick.getSymbol();
        double currentPrice = tick.getLtp();

        for (OpenPosition pos : positionBook.openPositions()) {
            if (pos.getSymbol().equals(symbol)) {
                checkRisk(pos, currentPrice);
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
        positionBook.remove(pos.getCorrelationId());
        log.info("[{}] Closing position reason={}: {}", pos.getSymbol(), reason, pos);

        BiConsumer<OpenPosition, ExitReason> handler = exitHandler;
        if (handler != null) {
            try {
                handler.accept(pos, reason);
            } catch (Exception e) {
                log.error("[{}] Exit handler failed: {}", pos.getSymbol(), e.getMessage());
            }
        }

        StrategyEvaluator se = strategyEvaluator;
        if (se != null) {
            se.onPositionClosed(pos.getSymbol());
        }
    }
}
