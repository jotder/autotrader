package com.rj.engine.risk;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyRiskConfig;
import com.rj.model.TradeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RiskSessionState {

    private static final Logger log = LoggerFactory.getLogger(RiskSessionState.class);

    private final RiskConfig riskConfig;

    private final AtomicBoolean killSwitchActive = new AtomicBoolean(false);
    private final AtomicBoolean dailyProfitLocked = new AtomicBoolean(false);
    private final AtomicBoolean anomalyMode      = new AtomicBoolean(false);
    private volatile String  anomalyReason;
    private volatile Instant anomalyTriggeredAt;
    private volatile double  dailyRealizedPnl  = 0;
    private volatile double  peakSessionEquity;
    private volatile double  currentOpenPnl    = 0;

    private final ConcurrentHashMap<String, AtomicInteger>     consecutiveLosses     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StrategyRiskConfig> strategyRiskOverrides = new ConcurrentHashMap<>();

    public RiskSessionState(RiskConfig riskConfig) {
        this.riskConfig       = riskConfig;
        this.peakSessionEquity = riskConfig.getInitialCapitalInr();
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public void recordClosedTrade(TradeRecord trade) {
        if (trade.getPnl() == null) return;
        dailyRealizedPnl += trade.getPnl();
        updatePeakEquity(0);

        AtomicInteger counter = consecutiveLosses.computeIfAbsent(
                trade.getStrategyId(), k -> new AtomicInteger(0));
        if (trade.isWinner()) {
            counter.set(0);
        } else {
            int newConsec = counter.incrementAndGet();
            if (newConsec >= riskConfig.getMaxConsecutiveLossesPerStrategy()) {
                log.warn("[{}] Strategy [{}] suspended: {} consecutive losses",
                        trade.getSymbol(), trade.getStrategyId(), newConsec);
            }
        }

        if (dailyRealizedPnl >= riskConfig.getMaxDailyProfitInr() && !dailyProfitLocked.get()) {
            dailyProfitLocked.set(true);
            log.warn("PROFIT LOCK: daily profit target reached — realizedPnl={}", dailyRealizedPnl);
        }
        log.info("Daily PnL updated: {} (trade PnL={})",
                String.format("%.2f", dailyRealizedPnl),
                String.format("%.2f", trade.getPnl()));
    }

    public void updateCurrentEquity(double totalOpenPnL) {
        this.currentOpenPnl = totalOpenPnL;
        updatePeakEquity(totalOpenPnL);
        if (checkDrawdown()) {
            triggerAnomaly("Drawdown Breached (Trailing)");
        }
    }

    private void updatePeakEquity(double totalOpenPnL) {
        double equity = riskConfig.getInitialCapitalInr() + dailyRealizedPnl + totalOpenPnL;
        if (equity > peakSessionEquity) peakSessionEquity = equity;
    }

    private boolean checkDrawdown() {
        double equity = riskConfig.getInitialCapitalInr() + dailyRealizedPnl + currentOpenPnl;
        double dd = (peakSessionEquity - equity) / peakSessionEquity;
        if (dd >= riskConfig.getMaxDrawdownPercent() / 100.0) {
            if (!killSwitchActive.get()) {
                activateKillSwitch(String.format("Drawdown limit breached: %.2f%%", dd * 100.0));
            }
            return true;
        }
        return false;
    }

    /** Called by PreTradeGate as a synchronous drawdown safety net. */
    public boolean checkDrawdownAndActivate() {
        return checkDrawdown();
    }

    public void triggerAnomaly(String reason) {
        if (anomalyMode.compareAndSet(false, true)) {
            anomalyReason      = reason;
            anomalyTriggeredAt = Instant.now();
            killSwitchActive.set(true);
            log.error("ANOMALY TRIGGERED: {} — all entries blocked, manual restart required", reason);
        }
    }

    public boolean acknowledgeAnomaly() {
        if (anomalyMode.compareAndSet(true, false)) {
            log.warn("ANOMALY ACKNOWLEDGED — anomaly mode cleared. Reason was: {}", anomalyReason);
            anomalyReason      = null;
            anomalyTriggeredAt = null;
            return true;
        }
        return false;
    }

    public void activateKillSwitch(String reason) {
        killSwitchActive.set(true);
        log.error("KILL SWITCH ACTIVATED: {}", reason);
    }

    public void resetDay() {
        if (anomalyMode.get()) {
            log.warn("Cannot reset day while in anomaly mode — acknowledge anomaly first");
            return;
        }
        dailyRealizedPnl = 0;
        killSwitchActive.set(false);
        dailyProfitLocked.set(false);
        consecutiveLosses.clear();
        log.info("RiskSessionState day reset complete");
    }

    public void applyStrategyRiskOverride(String strategyId, StrategyRiskConfig override) {
        if (override == null) throw new IllegalArgumentException("override must not be null");
        strategyRiskOverrides.put(strategyId, override);
        log.info("Applied per-strategy risk override for strategy '{}'", strategyId);
    }

    public void removeStrategyRiskOverride(String strategyId) {
        strategyRiskOverrides.remove(strategyId);
        log.info("Removed per-strategy risk override for strategy '{}'", strategyId);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isKillSwitchActive()    { return killSwitchActive.get(); }
    public boolean isDailyProfitLocked()   { return dailyProfitLocked.get(); }
    public boolean isAnomalyMode()         { return anomalyMode.get(); }
    public String  getAnomalyReason()      { return anomalyReason; }
    public Instant getAnomalyTriggeredAt() { return anomalyTriggeredAt; }
    public double  getDailyRealizedPnl()   { return dailyRealizedPnl; }

    public int getConsecutiveLosses(String strategyId) {
        AtomicInteger c = consecutiveLosses.get(strategyId);
        return c == null ? 0 : c.get();
    }

    public StrategyRiskConfig getStrategyRiskOverride(String strategyId) {
        return strategyRiskOverrides.get(strategyId);
    }
}
