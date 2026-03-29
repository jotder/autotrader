package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyRiskConfig;
import com.rj.config.TradeStrategyConfig;
import com.rj.model.OpenPosition;
import com.rj.model.TradeRecord;
import com.rj.model.TradeSignal;
import com.rj.risk.sizing.ISizingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pre-trade risk gate and daily state manager (Phase-II Pluggable).
 */
public class RiskManager {

    private static final Logger log = LoggerFactory.getLogger(RiskManager.class);

    private final RiskConfig riskConfig;
    private final java.util.function.Supplier<ZonedDateTime> clock;

    // Phase-II: Pluggable strategy configurations
    private final ConcurrentHashMap<String, TradeStrategyConfig> strategyConfigs = new ConcurrentHashMap<>();

    // Kill switches
    private final AtomicBoolean killSwitchActive = new AtomicBoolean(false);
    private final AtomicBoolean dailyProfitLocked = new AtomicBoolean(false);
    // Anomaly mode — requires manual acknowledgement to resume
    private final AtomicBoolean anomalyMode = new AtomicBoolean(false);
    private volatile String anomalyReason;
    private volatile java.time.Instant anomalyTriggeredAt;
    // Per-strategy consecutive loss counters (keyed by strategyId)
    private final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> consecutiveLosses
            = new java.util.concurrent.ConcurrentHashMap<>();
    // Per-strategy risk overrides loaded from YAML (keyed by strategyId)
    private final java.util.concurrent.ConcurrentHashMap<String, StrategyRiskConfig> strategyRiskOverrides
            = new java.util.concurrent.ConcurrentHashMap<>();
    // Daily running totals
    private volatile double dailyRealizedPnl = 0;
    private volatile double peakSessionEquity;
    private volatile double currentOpenPnl = 0;

    public RiskManager(RiskConfig riskConfig) {
        this(riskConfig, () -> ZonedDateTime.now(riskConfig.getExchangeZone()));
    }

    public RiskManager(RiskConfig riskConfig, java.util.function.Supplier<ZonedDateTime> clock) {
        this.riskConfig = riskConfig;
        this.clock = clock;
        this.peakSessionEquity = riskConfig.getInitialCapitalInr();
    }

    public void updateStrategyConfig(TradeStrategyConfig config) {
        this.strategyConfigs.put(config.getStrategyId(), config);
        log.info("Updated RiskManager config for strategy: {} [{}% capital]",
                config.getStrategyId(), config.getAllocationPercentage());
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

        // ── Gate 2: drawdown check ───────────────────────────────────────────
        if (checkDrawdown()) {
            return reject("Drawdown limit breached — kill switch active");
        }

        // ── Gate 3: daily profit lock ─────────────────────────────────────────
        if (dailyProfitLocked.get()) {
            return reject("Daily profit target reached (" + riskConfig.getMaxDailyProfitInr() + " INR) — no new entries");
        }

        // ── Gate 4: daily loss limit ──────────────────────────────────────────
        if (dailyRealizedPnl <= -riskConfig.getMaxDailyLossInr()) {
            killSwitchActive.set(true);
            log.error("KILL SWITCH: daily loss limit breached — realizedPnl={}", dailyRealizedPnl);
            return reject("Daily loss limit breached: " + String.format("%.2f", dailyRealizedPnl) + " INR");
        }

        // ── Gate 4: time cutoff ───────────────────────────────────────────────
        ZonedDateTime now = clock.get();
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

        // ── Gate 7: strategy-level capital & sizing ───────────────────────────
        TradeStrategyConfig stratCfg = strategyConfigs.get(signal.getStrategyId());
        if (stratCfg == null) {
            return reject("Strategy config not found for: " + signal.getStrategyId());
        }

        double strategyCapital = totalCapital * (stratCfg.getAllocationPercentage() / 100.0);
        ISizingModel sizingModel = stratCfg.createSizingModel();

        double rawQty = sizingModel.calculateQuantity(signal, strategyCapital);
        int lotSize = signal.getLotSize();
        int lotAlignedQty = (int) (Math.floor(rawQty / lotSize) * lotSize);

        // Ensure at least 1 lot if it's a derivative and we have some budget
        if (lotAlignedQty == 0 && lotSize > 1 && rawQty > 0) {
            lotAlignedQty = lotSize;
        }

        if (lotAlignedQty <= 0) {
            return reject(String.format("Insufficient strategy capital for [%s] (needed=%.2f qty, lot=%d)",
                    stratCfg.getName(), rawQty, lotSize));
        }

        // ── Gate 8: execution caps ───────────────────────────────────────────
        int maxQtyPerOrder = strategyRiskOverrides.containsKey(signal.getStrategyId())
                ? strategyRiskOverrides.get(signal.getStrategyId()).maxQty()
                : riskConfig.getMaxQuantityPerOrder();

        double symbolMaxExposureFraction = strategyRiskOverrides.containsKey(signal.getStrategyId())
                ? strategyRiskOverrides.get(signal.getStrategyId()).maxExposurePct() / 100.0
                : riskConfig.getMaxExposurePerSymbolPercent();
        double symbolMaxExposure = totalCapital * symbolMaxExposureFraction;
        int exposureCapQty = (int) Math.floor((symbolMaxExposure - currentExposure) / signal.getSuggestedEntry());

        int finalQty = Math.min(lotAlignedQty, Math.min(maxQtyPerOrder, exposureCapQty));

        // Final lot alignment check after all caps
        if (lotSize > 1) {
            finalQty = (finalQty / lotSize) * lotSize;
        }

        if (finalQty <= 0) {
            return reject(String.format("Quantity 0 after caps (lotAligned=%d, maxPerOrder=%d, exposureCap=%d)",
                    lotAlignedQty, maxQtyPerOrder, exposureCapQty));
        }

        log.info("[{}] Pre-trade OK: qty={} sizingModel={} strategyCap={} reason={}",
                signal.getSymbol(), finalQty, sizingModel.getName(),
                String.format("%.2f", strategyCapital), signal.getReason());

        return new PreTradeResult(true, finalQty, signal.getSuggestedStopLoss(), signal.getSuggestedTarget(), null);
    }

    // ── Manual kill switch ────────────────────────────────────────────────────

    /**
     * Updates daily PnL and strategy loss counters from a closed trade.
     * Must be called after every exit.
     */
    public void recordClosedTrade(TradeRecord trade) {
        if (trade.getPnl() == null) return;

        dailyRealizedPnl += trade.getPnl();
        updatePeakEquity(0); // Update peak based on realized change
        checkDrawdown(); // Check for drawdown breach after realization

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
     * Updates current equity tracking with latest open PnL.
     * Also checks for drawdown breach.
     */
    public void updateCurrentEquity(double totalOpenPnL) {
        this.currentOpenPnl = totalOpenPnL;
        updatePeakEquity(totalOpenPnL);
        if (checkDrawdown()) {
            triggerAnomaly("3% Drawdown Breached (Trailing)");
        }
    }

    private void updatePeakEquity(double totalOpenPnL) {
        double currentEquity = riskConfig.getInitialCapitalInr() + dailyRealizedPnl + totalOpenPnL;
        if (currentEquity > peakSessionEquity) {
            peakSessionEquity = currentEquity;
            log.debug("New session peak equity: {}", String.format("%.2f", peakSessionEquity));
        }
    }

    private boolean checkDrawdown() {
        double currentEquity = riskConfig.getInitialCapitalInr() + dailyRealizedPnl + currentOpenPnl;
        double dd = (peakSessionEquity - currentEquity) / peakSessionEquity;
        if (dd >= riskConfig.getMaxDrawdownPercent() / 100.0) {
            if (!killSwitchActive.get()) {
                activateKillSwitch(String.format("Drawdown limit breached: %.2f%%", dd * 100.0));
            }
            return true;
        }
        return false;
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

    // ── Anomaly mode ────────────────────────────────────────────────────────

    /**
     * Trigger anomaly mode: activates kill switch AND requires manual
     * acknowledgement before trading can resume.
     * <p>
     * Unlike a regular kill switch, anomaly mode cannot be cleared by
     * {@link #resetDay()} — it requires {@link #acknowledgeAnomaly()}.
     */
    public void triggerAnomaly(String reason) {
        if (anomalyMode.compareAndSet(false, true)) {
            anomalyReason = reason;
            anomalyTriggeredAt = java.time.Instant.now();
            killSwitchActive.set(true);
            log.error("ANOMALY TRIGGERED: {} — all entries blocked, manual restart required", reason);
        }
    }

    /**
     * Acknowledge and clear anomaly mode. Only after this call will
     * {@link #resetDay()} be effective.
     *
     * @return true if anomaly was active and has been cleared
     */
    public boolean acknowledgeAnomaly() {
        if (anomalyMode.compareAndSet(true, false)) {
            log.warn("ANOMALY ACKNOWLEDGED — anomaly mode cleared. Reason was: {}", anomalyReason);
            anomalyReason = null;
            anomalyTriggeredAt = null;
            return true;
        }
        return false;
    }

    public boolean isAnomalyMode() { return anomalyMode.get(); }
    public String getAnomalyReason() { return anomalyReason; }
    public java.time.Instant getAnomalyTriggeredAt() { return anomalyTriggeredAt; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public void resetDay() {
        if (anomalyMode.get()) {
            log.warn("Cannot reset day while in anomaly mode — acknowledge anomaly first");
            return;
        }
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
