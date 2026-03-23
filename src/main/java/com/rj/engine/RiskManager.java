package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyRiskConfig;
import com.rj.model.OpenPosition;
import com.rj.model.TradeRecord;
import com.rj.model.TradeSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pre-trade risk gate and daily state manager.
 *
 * <h3>Checks performed in {@link #preTradeCheck}</h3>
 * <ol>
 *   <li>Kill switch active (manually set or triggered by loss limit)</li>
 *   <li>Daily profit lock</li>
 *   <li>Daily loss limit</li>
 *   <li>No-new-trades time cutoff</li>
 *   <li>Max capital exposure per symbol</li>
 *   <li>Position sizing: qty = floor(risk_budget / risk_per_unit), lot-aligned, capped</li>
 *   <li>Fat-finger guard: qty ≤ maxQtyPerOrder</li>
 * </ol>
 *
 * <p>Call {@link #recordClosedTrade} after every exit so daily PnL stays current.
 * Call {@link #resetDay} at the start of each trading session.</p>
 */
public class RiskManager {

    private static final Logger log = LoggerFactory.getLogger(RiskManager.class);

    private final RiskConfig riskConfig;
    // Kill switches
    private final AtomicBoolean killSwitchActive = new AtomicBoolean(false);
    private final AtomicBoolean dailyProfitLocked = new AtomicBoolean(false);
    // Per-strategy consecutive loss counters (keyed by strategyId)
    private final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> consecutiveLosses
            = new java.util.concurrent.ConcurrentHashMap<>();
    // Per-strategy risk overrides loaded from YAML (keyed by strategyId)
    private final java.util.concurrent.ConcurrentHashMap<String, StrategyRiskConfig> strategyRiskOverrides
            = new java.util.concurrent.ConcurrentHashMap<>();
    // Daily running totals
    private volatile double dailyRealizedPnl = 0;

    public RiskManager(RiskConfig riskConfig) {
        this.riskConfig = riskConfig;
    }

    // ── Pre-trade check ───────────────────────────────────────────────────────

    private static PreTradeResult reject(String reason) {
        log.info("Pre-trade REJECTED: {}", reason);
        return new PreTradeResult(false, 0, 0, 0, reason);
    }

    // ── Post-trade accounting ─────────────────────────────────────────────────

    /**
     * Runs all pre-trade risk gates.  Returns a result indicating whether the trade
     * is approved, and if so the computed quantity, stop-loss, and take-profit.
     *
     * @param signal        the compound trade signal from StrategyEvaluator
     * @param openPositions current open positions (for exposure calculation)
     * @param totalCapital  account capital used for sizing
     */
    public PreTradeResult preTradeCheck(TradeSignal signal,
                                        Collection<OpenPosition> openPositions,
                                        double totalCapital) {
        // ── Gate 1: kill switch ───────────────────────────────────────────────
        if (killSwitchActive.get()) {
            return reject("Kill switch active — trading halted for the day");
        }

        // ── Gate 2: daily profit lock ─────────────────────────────────────────
        if (dailyProfitLocked.get()) {
            return reject("Daily profit target reached (" + riskConfig.getMaxDailyProfitInr() + " INR) — no new entries");
        }

        // ── Gate 3: daily loss limit ──────────────────────────────────────────
        if (dailyRealizedPnl <= -riskConfig.getMaxDailyLossInr()) {
            killSwitchActive.set(true);
            log.error("KILL SWITCH: daily loss limit breached — realizedPnl={}", dailyRealizedPnl);
            return reject("Daily loss limit breached: " + String.format("%.2f", dailyRealizedPnl) + " INR");
        }

        // ── Gate 4: time cutoff ───────────────────────────────────────────────
        ZonedDateTime now = ZonedDateTime.now(riskConfig.getExchangeZone());
        if (now.toLocalTime().isAfter(riskConfig.getNoNewTradesAfter())) {
            return reject("Past no-new-trades cutoff " + riskConfig.getNoNewTradesAfter() + " IST");
        }

        // ── Gate 5: consecutive loss limit per strategy ───────────────────────
        StrategyRiskConfig stratOverride = strategyRiskOverrides.get(signal.getStrategyId());
        int maxConsecLosses = stratOverride != null
                ? stratOverride.maxConsecutiveLosses()
                : riskConfig.getMaxConsecutiveLossesPerStrategy();
        int consec = consecutiveLosses
                .computeIfAbsent(signal.getStrategyId(), k -> new AtomicInteger(0))
                .get();
        if (consec >= maxConsecLosses) {
            return reject("Strategy [" + signal.getStrategyId() + "] suspended: "
                    + consec + " consecutive losses");
        }

        // ── Gate 6: max exposure per symbol ──────────────────────────────────
        double currentExposure = openPositions.stream()
                .filter(p -> p.getSymbol().equals(signal.getSymbol()))
                .mapToDouble(p -> p.getEntryPrice() * p.getQuantity())
                .sum();
        double maxExposureFraction = stratOverride != null
                ? stratOverride.maxExposurePct() / 100.0
                : riskConfig.getMaxExposurePerSymbolPercent();
        double maxExposure = totalCapital * maxExposureFraction;
        if (currentExposure >= maxExposure) {
            return reject(String.format("Max exposure per symbol exceeded: %.0f >= %.0f",
                    currentExposure, maxExposure));
        }

        // ── Gate 7: position sizing ───────────────────────────────────────────
        double entry = signal.getSuggestedEntry();
        double sl = signal.getSuggestedStopLoss();
        double tp = signal.getSuggestedTarget();
        double riskPerUnit = Math.abs(entry - sl);

        if (riskPerUnit <= 0) {
            return reject("Stop loss is at or beyond entry price — risk per unit = 0");
        }

        double riskFraction = stratOverride != null
                ? stratOverride.riskPerTradePct() / 100.0
                : riskConfig.getMaxRiskPerTradePercent();
        double riskBudget = totalCapital * riskFraction;
        int rawQty = (int) Math.floor(riskBudget / riskPerUnit);
        int lotSize = riskConfig.getInstrumentLotSize();
        int lotAlignedQty = lotSize > 1 ? (rawQty / lotSize) * lotSize : rawQty;

        int maxQtyPerOrder = stratOverride != null
                ? stratOverride.maxQty()
                : riskConfig.getMaxQuantityPerOrder();
        int exposureCapQty = (int) Math.floor((maxExposure - currentExposure) / entry);
        int finalQty = Math.min(lotAlignedQty,
                Math.min(maxQtyPerOrder, exposureCapQty));

        if (finalQty <= 0) {
            return reject("Position sizing yields quantity 0 (risk budget too small for this SL distance)");
        }

        log.info("[{}] Pre-trade OK: qty={} riskPerUnit={} riskBudget={} dailyPnl={}",
                signal.getSymbol(), finalQty,
                String.format("%.2f", riskPerUnit),
                String.format("%.2f", riskBudget),
                String.format("%.2f", dailyRealizedPnl));

        return new PreTradeResult(true, finalQty, sl, tp, null);
    }

    // ── Manual kill switch ────────────────────────────────────────────────────

    /**
     * Updates daily PnL and strategy loss counters from a closed trade.
     * Must be called after every exit.
     */
    public void recordClosedTrade(TradeRecord trade) {
        if (trade.getPnl() == null) return;

        dailyRealizedPnl += trade.getPnl();

        // Update consecutive loss counter for the strategy
        AtomicInteger counter = consecutiveLosses
                .computeIfAbsent(trade.getStrategyId(), k -> new AtomicInteger(0));
        if (trade.isWinner()) {
            counter.set(0);
        } else {
            int newConsec = counter.incrementAndGet();
            if (newConsec >= riskConfig.getMaxConsecutiveLossesPerStrategy()) {
                log.warn("[{}] Strategy [{}] suspended: {} consecutive losses",
                        trade.getSymbol(), trade.getStrategyId(), newConsec);
            }
        }

        // Check daily profit lock
        if (dailyRealizedPnl >= riskConfig.getMaxDailyProfitInr() && !dailyProfitLocked.get()) {
            dailyProfitLocked.set(true);
            log.warn("PROFIT LOCK: daily profit target reached — realizedPnl={}", dailyRealizedPnl);
        }

        log.info("Daily PnL updated: {} (trade PnL={})",
                String.format("%.2f", dailyRealizedPnl),
                String.format("%.2f", trade.getPnl()));
    }

    /**
     * Applies a per-strategy risk config override.
     * When set, the override values take precedence over global {@link RiskConfig}
     * for the given strategy in all {@link #preTradeCheck} evaluations.
     *
     * @param strategyId unique strategy identifier matching {@link com.rj.model.TradeSignal#getStrategyId()}
     * @param override   risk config loaded from YAML; must not be {@code null}
     */
    public void applyStrategyRiskOverride(String strategyId, StrategyRiskConfig override) {
        if (override == null) throw new IllegalArgumentException("override must not be null");
        strategyRiskOverrides.put(strategyId, override);
        log.info("Applied per-strategy risk override for strategy '{}'", strategyId);
    }

    /**
     * Removes a previously applied per-strategy risk override,
     * reverting to global {@link RiskConfig} values.
     */
    public void removeStrategyRiskOverride(String strategyId) {
        strategyRiskOverrides.remove(strategyId);
        log.info("Removed per-strategy risk override for strategy '{}'", strategyId);
    }

    public void activateKillSwitch(String reason) {
        killSwitchActive.set(true);
        log.error("KILL SWITCH ACTIVATED: {}", reason);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public void resetDay() {
        dailyRealizedPnl = 0;
        killSwitchActive.set(false);
        dailyProfitLocked.set(false);
        consecutiveLosses.clear();
        log.info("RiskManager day reset complete");
    }

    public double getDailyRealizedPnl() {
        return dailyRealizedPnl;
    }

    public boolean isKillSwitchActive() {
        return killSwitchActive.get();
    }

    // ── Result type ───────────────────────────────────────────────────────────

    public boolean isDailyProfitLocked() {
        return dailyProfitLocked.get();
    }

    public record PreTradeResult(
            boolean approved,
            int quantity,
            double stopLoss,
            double takeProfit,
            String rejectReason) {
    }
}
