package com.rj.engine.risk;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyRiskConfig;
import com.rj.config.TradeStrategyConfig;
import com.rj.model.OpenPosition;
import com.rj.model.TradeSignal;
import com.rj.risk.sizing.ISizingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Stateless 8-gate pre-trade risk filter extracted from RiskManager (Phase-3).
 * All mutable session state is delegated to {@link RiskSessionState}.
 */
public class PreTradeGate {

    private static final Logger log = LoggerFactory.getLogger(PreTradeGate.class);

    private final RiskConfig riskConfig;
    private final RiskSessionState riskSessionState;
    private final Supplier<ZonedDateTime> clock;

    /** Per-strategy configs registered by EngineConfiguration or live updates. */
    private final ConcurrentHashMap<String, TradeStrategyConfig> strategyConfigs = new ConcurrentHashMap<>();

    /** Production constructor — uses live clock in exchange zone. */
    public PreTradeGate(RiskConfig riskConfig, RiskSessionState riskSessionState) {
        this(riskConfig, riskSessionState,
                () -> ZonedDateTime.now(riskConfig.getExchangeZone()));
    }

    /** Testable constructor — clock is injected for deterministic time-gate tests. */
    public PreTradeGate(RiskConfig riskConfig, RiskSessionState riskSessionState,
                        Supplier<ZonedDateTime> clock) {
        this.riskConfig       = riskConfig;
        this.riskSessionState = riskSessionState;
        this.clock            = clock;
    }

    // ── Strategy config management ────────────────────────────────────────────

    public void updateStrategyConfig(TradeStrategyConfig config) {
        strategyConfigs.put(config.getStrategyId(), config);
        log.info("PreTradeGate: registered strategy config for '{}' [{}% capital]",
                config.getStrategyId(), config.getAllocationPercentage());
    }

    // ── Override delegation ───────────────────────────────────────────────────

    public void applyStrategyRiskOverride(String strategyId, StrategyRiskConfig override) {
        riskSessionState.applyStrategyRiskOverride(strategyId, override);
    }

    public void removeStrategyRiskOverride(String strategyId) {
        riskSessionState.removeStrategyRiskOverride(strategyId);
    }

    // ── Pre-trade check ───────────────────────────────────────────────────────

    /**
     * Runs all 8 pre-trade risk gates.
     *
     * @param signal        compound trade signal from StrategyEvaluator
     * @param openPositions current open positions (for exposure calculation)
     * @param totalCapital  account capital used for sizing
     * @return approved result with quantity/sl/tp, or rejected result with reason
     */
    public PreTradeResult preTradeCheck(TradeSignal signal,
                                        Collection<OpenPosition> openPositions,
                                        double totalCapital) {

        // ── Gate 1: kill switch ───────────────────────────────────────────────
        if (riskSessionState.isKillSwitchActive()) {
            return reject("Kill switch active — trading halted for the day");
        }

        // ── Gate 2: drawdown double-check ─────────────────────────────────────
        if (riskSessionState.checkDrawdownAndActivate()) {
            return reject("Drawdown limit breached — kill switch active");
        }

        // ── Gate 3: daily profit lock ─────────────────────────────────────────
        if (riskSessionState.isDailyProfitLocked()) {
            return reject("Daily profit target reached (" + riskConfig.getMaxDailyProfitInr()
                    + " INR) — no new entries");
        }

        // ── Gate 4: daily loss limit ──────────────────────────────────────────
        if (riskSessionState.getDailyRealizedPnl() <= -riskConfig.getMaxDailyLossInr()) {
            return reject("Daily loss limit breached: "
                    + String.format("%.2f", riskSessionState.getDailyRealizedPnl()) + " INR");
        }

        // ── Gate 5: time cutoff ───────────────────────────────────────────────
        ZonedDateTime now = clock.get();
        if (now.toLocalTime().isAfter(riskConfig.getNoNewTradesAfter())) {
            return reject("Past no-new-trades cutoff " + riskConfig.getNoNewTradesAfter() + " IST");
        }

        // ── Gate 6: consecutive loss limit per strategy ───────────────────────
        StrategyRiskConfig stratOverride = riskSessionState.getStrategyRiskOverride(signal.getStrategyId());
        int maxConsecLosses = stratOverride != null
                ? stratOverride.maxConsecutiveLosses()
                : riskConfig.getMaxConsecutiveLossesPerStrategy();
        int consec = riskSessionState.getConsecutiveLosses(signal.getStrategyId());
        if (consec >= maxConsecLosses) {
            return reject("Strategy [" + signal.getStrategyId() + "] suspended: "
                    + consec + " consecutive losses");
        }

        // ── Gate 7: max exposure per symbol ──────────────────────────────────
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

        // ── Gate 8: strategy-level capital & sizing ───────────────────────────
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

        // Execution caps
        int maxQtyPerOrder = stratOverride != null
                ? stratOverride.maxQty()
                : riskConfig.getMaxQuantityPerOrder();

        double symbolMaxExposureFraction = stratOverride != null
                ? stratOverride.maxExposurePct() / 100.0
                : riskConfig.getMaxExposurePerSymbolPercent();
        double symbolMaxExposure = totalCapital * symbolMaxExposureFraction;
        int exposureCapQty = (int) Math.floor(
                (symbolMaxExposure - currentExposure) / signal.getSuggestedEntry());

        int finalQty = Math.min(lotAlignedQty, Math.min(maxQtyPerOrder, exposureCapQty));

        // Final lot alignment after all caps
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

        return new PreTradeResult(true, finalQty,
                signal.getSuggestedStopLoss(), signal.getSuggestedTarget(), null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static PreTradeResult reject(String reason) {
        log.info("Pre-trade REJECTED: {}", reason);
        return new PreTradeResult(false, 0, 0, 0, reason);
    }
}
