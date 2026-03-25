package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.model.OpenPosition;
import com.rj.model.PositionsSummary;
import com.rj.model.Signal;
import com.rj.model.Timeframe;
import com.rj.model.TradeRecord;
import com.rj.model.ExecutionMode;
import fyers.FyersPositions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.EnumMap;

/**
 * Startup reconciliation — diffs broker positions against engine's in-memory state.
 *
 * <p>Runs once at startup, <b>before</b> {@link PositionMonitor#start()}.
 * If the broker API is unreachable, startup is aborted (safety first).</p>
 *
 * <h3>Reconciliation scenarios</h3>
 * <ul>
 *   <li><b>Orphaned in broker</b> — broker has position, engine doesn't → adopt with conservative SL</li>
 *   <li><b>Stale in engine</b> — engine has position, broker doesn't → remove from engine</li>
 *   <li><b>Qty mismatch</b> — both have position but different quantities → log warning</li>
 *   <li><b>Match</b> — both agree → no action</li>
 * </ul>
 */
public class PositionReconciler {

    private static final Logger log = LoggerFactory.getLogger(PositionReconciler.class);
    private static final double DEFAULT_SL_PERCENT = 0.02; // 2% fallback SL for adopted positions

    private final FyersPositions fyersPositions;
    private final PositionMonitor positionMonitor;
    private final ConcurrentHashMap<String, TradeRecord> openRecords;
    private final TradeJournal journal;
    private final RiskConfig riskConfig;
    private final BrokerCircuitBreaker circuitBreaker; // nullable

    private volatile ReconciliationResult lastResult;

    public PositionReconciler(FyersPositions fyersPositions,
                              PositionMonitor positionMonitor,
                              ConcurrentHashMap<String, TradeRecord> openRecords,
                              TradeJournal journal,
                              RiskConfig riskConfig) {
        this(fyersPositions, positionMonitor, openRecords, journal, riskConfig, null);
    }

    public PositionReconciler(FyersPositions fyersPositions,
                              PositionMonitor positionMonitor,
                              ConcurrentHashMap<String, TradeRecord> openRecords,
                              TradeJournal journal,
                              RiskConfig riskConfig,
                              BrokerCircuitBreaker circuitBreaker) {
        this.fyersPositions = fyersPositions;
        this.positionMonitor = positionMonitor;
        this.openRecords = openRecords;
        this.journal = journal;
        this.riskConfig = riskConfig;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * Performs the broker ↔ engine reconciliation.
     *
     * @return reconciliation result with counts and details
     * @throws ReconciliationException if broker API fails (startup must abort)
     */
    public ReconciliationResult reconcile() {
        log.info("Position reconciliation starting...");

        // ── 1. Fetch broker positions (via circuit breaker if available) ─────────
        PositionsSummary brokerState = circuitBreaker != null
                ? circuitBreaker.execute(() -> fyersPositions.getPositions(), true)
                : fyersPositions.getPositions();
        if (brokerState == null) {
            throw new ReconciliationException(
                    "Broker API returned null — cannot reconcile positions. Aborting startup.");
        }

        // Filter to truly open positions (netQty != 0)
        List<PositionsSummary.NetPosition> openBrokerPositions = brokerState.netPositions.stream()
                .filter(p -> p.netQty != 0)
                .toList();

        // ── 2. Build lookup maps ────────────────────────────────────────────────
        // Engine positions keyed by "symbol:direction"
        Map<String, OpenPosition> engineMap = new HashMap<>();
        for (OpenPosition ep : positionMonitor.openPositions()) {
            String key = reconciliationKey(ep.getSymbol(), ep.getDirection() == Signal.BUY ? 1 : -1);
            engineMap.put(key, ep);
        }

        // Broker positions keyed by "symbol:direction"
        Map<String, PositionsSummary.NetPosition> brokerMap = new HashMap<>();
        for (PositionsSummary.NetPosition bp : openBrokerPositions) {
            int side = bp.netQty > 0 ? 1 : -1;
            String key = reconciliationKey(bp.symbol, side);
            brokerMap.put(key, bp);
        }

        // ── 3. Reconcile ────────────────────────────────────────────────────────
        int adopted = 0, removed = 0, matched = 0, qtyMismatch = 0;
        List<String> details = new ArrayList<>();

        // 3a. Check each broker position against engine
        for (var entry : brokerMap.entrySet()) {
            String key = entry.getKey();
            PositionsSummary.NetPosition bp = entry.getValue();

            if (engineMap.containsKey(key)) {
                // Both have it — verify qty
                OpenPosition ep = engineMap.get(key);
                int brokerQty = Math.abs(bp.netQty);
                if (ep.getQuantity() != brokerQty) {
                    qtyMismatch++;
                    String detail = String.format("QTY_MISMATCH %s: broker=%d engine=%d",
                            bp.symbol, brokerQty, ep.getQuantity());
                    details.add(detail);
                    journal.logReconciliation("QTY_MISMATCH", bp.symbol, brokerQty, detail);
                    log.warn("[RECONCILE] {}", detail);
                } else {
                    matched++;
                    log.info("[RECONCILE] MATCH {}: qty={}", bp.symbol, brokerQty);
                }
            } else {
                // Broker has it, engine doesn't → adopt
                adopted++;
                adoptBrokerPosition(bp);
                String detail = String.format("ADOPTED %s: qty=%d avg=%.2f side=%s",
                        bp.symbol, Math.abs(bp.netQty), bp.netAvg,
                        bp.netQty > 0 ? "LONG" : "SHORT");
                details.add(detail);
                journal.logReconciliation("ADOPTED", bp.symbol, Math.abs(bp.netQty), detail);
                log.warn("[RECONCILE] {}", detail);
            }
        }

        // 3b. Check for stale engine positions not in broker
        for (var entry : engineMap.entrySet()) {
            String key = entry.getKey();
            if (!brokerMap.containsKey(key)) {
                removed++;
                OpenPosition stale = entry.getValue();
                positionMonitor.removePosition(stale.getCorrelationId());
                openRecords.remove(stale.getCorrelationId());
                String detail = String.format("REMOVED_STALE %s: correlationId=%s",
                        stale.getSymbol(), stale.getCorrelationId());
                details.add(detail);
                journal.logReconciliation("REMOVED_STALE", stale.getSymbol(),
                        stale.getQuantity(), detail);
                log.warn("[RECONCILE] {}", detail);
            }
        }

        ReconciliationResult result = new ReconciliationResult(
                adopted, removed, matched, qtyMismatch, details);
        this.lastResult = result;

        log.info("Position reconciliation complete: {}", result);
        return result;
    }

    /**
     * Adopts a broker position into the engine with a conservative stop-loss.
     * No take-profit is set — position will be exited manually or at market close.
     */
    private void adoptBrokerPosition(PositionsSummary.NetPosition bp) {
        Signal direction = bp.netQty > 0 ? Signal.BUY : Signal.SELL;
        int qty = Math.abs(bp.netQty);
        double entryPrice = bp.netAvg;

        // Conservative SL: maxRiskPerTradePercent from entry price (default 2%)
        double slPct = riskConfig.getMaxRiskPerTradePercent();
        if (slPct <= 0) slPct = DEFAULT_SL_PERCENT;
        double stopLoss = direction == Signal.BUY
                ? entryPrice * (1.0 - slPct)
                : entryPrice * (1.0 + slPct);

        // No TP — will square-off at market close or manual exit
        double takeProfit = 0;

        String correlationId = "reconciled-" + bp.symbol + "-" + Instant.now().toEpochMilli();
        Instant entryTime = Instant.now(); // approximate — actual fill time unknown

        OpenPosition position = new OpenPosition(
                bp.symbol, correlationId, "reconciled",
                direction, entryPrice, qty,
                stopLoss, takeProfit, entryTime);

        // TradeRecord needs a non-empty EnumMap for timeframeVotes
        Map<Timeframe, Signal> votes = new EnumMap<>(Timeframe.class);
        votes.put(Timeframe.M5, direction); // placeholder vote for reconciled positions

        TradeRecord record = new TradeRecord(
                correlationId, bp.symbol, "reconciled",
                ExecutionMode.LIVE, direction,
                entryPrice, qty, stopLoss, takeProfit,
                entryTime, 0, 0, votes);

        positionMonitor.addPosition(position);
        openRecords.put(correlationId, record);

        log.info("[RECONCILE] Adopted {} {} qty={} entry={} sl={}",
                bp.symbol, direction, qty,
                String.format("%.2f", entryPrice),
                String.format("%.2f", stopLoss));
    }

    /** Returns the last reconciliation result, or null if not yet run. */
    public ReconciliationResult getLastResult() {
        return lastResult;
    }

    private static String reconciliationKey(String symbol, int side) {
        return symbol + ":" + (side > 0 ? "LONG" : "SHORT");
    }

    // ── Result record ───────────────────────────────────────────────────────────

    public record ReconciliationResult(
            int adopted,
            int removed,
            int matched,
            int qtyMismatch,
            List<String> details
    ) {
        @Override
        public String toString() {
            return String.format("ReconciliationResult{adopted=%d, removed=%d, matched=%d, qtyMismatch=%d}",
                    adopted, removed, matched, qtyMismatch);
        }
    }

    // ── Exception ───────────────────────────────────────────────────────────────

    public static class ReconciliationException extends RuntimeException {
        public ReconciliationException(String message) {
            super(message);
        }
    }
}
