package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.engine.*;
import com.rj.model.*;
import com.rj.web.dto.ActionResponse;
import com.rj.web.dto.RiskResponse;
import com.rj.web.dto.TickResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class EngineController {

    private final TradingEngine engine;
    private final TickStore tickStore;
    private final ConfigManager configManager;

    public EngineController(TradingEngine engine, TickStore tickStore, ConfigManager configManager) {
        this.engine = engine;
        this.tickStore = tickStore;
        this.configManager = configManager;
    }

    // ── Read endpoints ──────────────────────────────────────────────────────

    @GetMapping("/positions")
    public Collection<OpenPosition> positions() {
        return engine.getPositionMonitor().openPositions();
    }

    @GetMapping("/trades")
    public List<TradeRecord> trades() {
        return engine.getJournal().closedTrades();
    }

    @GetMapping("/metrics")
    public StrategyAnalyzer.Report metrics() {
        return engine.analyzeSession();
    }

    @GetMapping("/risk")
    public RiskResponse risk() {
        RiskManager rm = engine.getRiskManager();
        var cfg = configManager.getRiskConfig();
        return new RiskResponse(
                rm.getDailyRealizedPnl(),
                rm.isKillSwitchActive(),
                rm.isDailyProfitLocked(),
                cfg.getMaxDailyLossInr(),
                cfg.getMaxDailyProfitInr(),
                cfg.getInitialCapitalInr()
        );
    }

    @GetMapping("/ticks/{symbol}")
    public ResponseEntity<TickResponse> ticks(@PathVariable String symbol) {
        TickBuffer buffer = tickStore.bufferFor(symbol);
        if (buffer == null) {
            return ResponseEntity.notFound().build();
        }

        List<Tick> snapshot = buffer.snapshot();
        Tick latest = snapshot.isEmpty() ? null : snapshot.getLast();

        return ResponseEntity.ok(new TickResponse(
                symbol,
                latest != null ? latest.getLtp() : 0,
                buffer.size(),
                buffer.newestTime()
        ));
    }

    // ── Write endpoints ─────────────────────────────────────────────────────

    @PostMapping("/kill")
    public ActionResponse kill(@RequestParam(defaultValue = "Manual kill via REST API") String reason) {
        engine.getRiskManager().activateKillSwitch(reason);
        return new ActionResponse(true, "Kill switch activated: " + reason);
    }

    @PostMapping("/reset")
    public ActionResponse reset() {
        engine.getRiskManager().resetDay();
        return new ActionResponse(true, "Daily risk state reset");
    }

    @PostMapping("/exit/{correlationId}")
    public ActionResponse exit(@PathVariable String correlationId) {
        PositionMonitor pm = engine.getPositionMonitor();
        try {
            pm.requestManualExit(correlationId);
            return new ActionResponse(true, "Manual exit requested for " + correlationId);
        } catch (IllegalArgumentException e) {
            return new ActionResponse(false, e.getMessage());
        }
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<?> reconciliation() {
        PositionReconciler reconciler = engine.getPositionReconciler();
        if (reconciler == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "skipped",
                    "reason", "Reconciliation only runs in LIVE mode"));
        }
        PositionReconciler.ReconciliationResult result = reconciler.getLastResult();
        if (result == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "pending",
                    "reason", "Reconciliation has not run yet"));
        }
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "adopted", result.adopted(),
                "removed", result.removed(),
                "matched", result.matched(),
                "qtyMismatch", result.qtyMismatch(),
                "details", result.details()));
    }

    // ── OMS Orders endpoint ───────────────────────────────────────────────

    @GetMapping("/orders")
    public Map<String, Object> orders() {
        var tracker = engine.getOrderTracker();
        return Map.of(
                "activeCount", tracker.activeCount(),
                "active", tracker.activeOrders(),
                "recentCompleted", tracker.completedOrders());
    }

    // ── Token refresh endpoints ───────────────────────────────────────────

    @GetMapping("/token/status")
    public Map<String, Object> tokenStatus() {
        var scheduler = engine.getTokenRefreshScheduler();
        var result = new LinkedHashMap<String, Object>();
        result.put("autoRefreshRunning", scheduler != null && scheduler.isRunning());
        result.put("lastRefreshStatus", scheduler != null ? scheduler.getLastRefreshStatus() : "n/a");
        result.put("lastRefreshTime", scheduler != null ? scheduler.getLastRefreshTime() : null);
        return result;
    }

    @PostMapping("/token/refresh")
    public ActionResponse tokenRefresh() {
        var scheduler = engine.getTokenRefreshScheduler();
        if (scheduler == null) {
            return new ActionResponse(false, "Token refresh scheduler not available");
        }
        boolean success = scheduler.refreshNow();
        return new ActionResponse(success,
                success ? "Token refreshed successfully" : "Token refresh failed — check logs");
    }

    // ── Anomaly protection endpoints ──────────────────────────────────────

    @GetMapping("/anomaly/status")
    public Map<String, Object> anomalyStatus() {
        RiskManager rm = engine.getRiskManager();
        var result = new LinkedHashMap<String, Object>();
        result.put("anomalyMode", rm.isAnomalyMode());
        result.put("reason", rm.getAnomalyReason());
        result.put("triggeredAt", rm.getAnomalyTriggeredAt());
        result.put("killSwitchActive", rm.isKillSwitchActive());
        var detector = engine.getAnomalyDetector();
        if (detector != null) {
            result.put("detectorTriggered", detector.isTriggered());
            result.put("consecutiveBrokerErrors", detector.getConsecutiveBrokerErrors());
        }
        return result;
    }

    @PostMapping("/emergency-flatten")
    public ActionResponse emergencyFlatten(@RequestParam(defaultValue = "Manual emergency flatten via REST") String reason) {
        int closed = engine.flattenAll(reason);
        return new ActionResponse(true,
                "Emergency flatten complete: " + closed + " positions closed. Anomaly mode active.");
    }

    @PostMapping("/anomaly/acknowledge")
    public ActionResponse acknowledgeAnomaly() {
        RiskManager rm = engine.getRiskManager();
        boolean cleared = rm.acknowledgeAnomaly();
        if (cleared) {
            var detector = engine.getAnomalyDetector();
            if (detector != null) detector.reset();
            return new ActionResponse(true, "Anomaly acknowledged and cleared. Use POST /api/reset to resume trading.");
        }
        return new ActionResponse(false, "No active anomaly to acknowledge");
    }

    // ── Circuit breaker endpoint ──────────────────────────────────────────

    @GetMapping("/circuit-breaker/status")
    public Map<String, Object> circuitBreakerStatus() {
        var cb = engine.getCircuitBreaker();
        var result = new LinkedHashMap<String, Object>();
        if (cb == null) {
            result.put("available", false);
            return result;
        }
        result.put("available", true);
        result.put("state", cb.getState().name());
        result.put("consecutiveFailures", cb.getConsecutiveFailures());
        result.put("daily429Count", cb.getDaily429Count());
        result.put("lastFailureTime", cb.getLastFailureTime());
        result.put("openedAt", cb.getOpenedAt());
        return result;
    }

    @PostMapping("/circuit-breaker/reset")
    public ActionResponse circuitBreakerReset() {
        var cb = engine.getCircuitBreaker();
        if (cb == null) {
            return new ActionResponse(false, "Circuit breaker not available");
        }
        cb.forceClose();
        return new ActionResponse(true, "Circuit breaker force-closed");
    }
}
