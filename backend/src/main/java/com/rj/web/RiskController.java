package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.engine.AnomalyDetector;
import com.rj.engine.TradingEngine;
import com.rj.engine.risk.PreTradeGate;
import com.rj.engine.risk.PreTradeResult;
import com.rj.engine.risk.RiskSessionState;
import com.rj.model.Confidence;
import com.rj.model.TradeSignal;
import com.rj.web.dto.ActionResponse;
import com.rj.web.dto.RiskResponse;
import com.rj.web.dto.SizingRequest;
import com.rj.web.dto.SizingResponse;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RiskController {

    private final TradingEngine engine;
    private final ConfigManager configManager;

    public RiskController(TradingEngine engine, ConfigManager configManager) {
        this.engine = engine;
        this.configManager = configManager;
    }

    @GetMapping("/risk")
    public RiskResponse risk() {
        RiskSessionState rs = engine.getRiskSessionState();
        var cfg = configManager.getRiskConfig();
        return new RiskResponse(
                rs.getDailyRealizedPnl(),
                rs.isKillSwitchActive(),
                rs.isDailyProfitLocked(),
                cfg.getMaxDailyLossInr(),
                cfg.getMaxDailyProfitInr(),
                cfg.getInitialCapitalInr()
        );
    }

    @PostMapping("/risk/calculate-sizing")
    public SizingResponse calculateSizing(@RequestBody SizingRequest request) {
        TradeSignal dummySignal = TradeSignal.builder()
                .symbol(request.symbol())
                .strategyId(request.strategyId())
                .suggestedEntry(request.entryPrice())
                .suggestedStopLoss(request.stopLoss())
                .suggestedTarget(request.entryPrice() * 1.02)
                .confidenceLevel(request.confidence())
                .atr(request.atr() > 0 ? request.atr() : request.entryPrice() * 0.01)
                .build();

        PreTradeResult result = engine.getPreTradeGate().preTradeCheck(
                dummySignal,
                engine.getPositionBook().openPositions(),
                configManager.getRiskConfig().getInitialCapitalInr()
        );

        return new SizingResponse(
                result.approved(),
                result.quantity(),
                result.stopLoss(),
                result.takeProfit(),
                result.rejectReason()
        );
    }

    @GetMapping("/anomaly/status")
    public Map<String, Object> anomalyStatus() {
        RiskSessionState rs = engine.getRiskSessionState();
        var result = new LinkedHashMap<String, Object>();
        result.put("anomalyMode", rs.isAnomalyMode());
        result.put("reason", rs.getAnomalyReason());
        result.put("triggeredAt", rs.getAnomalyTriggeredAt());
        result.put("killSwitchActive", rs.isKillSwitchActive());
        AnomalyDetector detector = engine.getAnomalyDetector();
        if (detector != null) {
            result.put("detectorTriggered", detector.isTriggered());
            result.put("consecutiveBrokerErrors", detector.getConsecutiveBrokerErrors());
        }
        return result;
    }

    @PostMapping("/anomaly/acknowledge")
    public ActionResponse acknowledgeAnomaly() {
        RiskSessionState rs = engine.getRiskSessionState();
        boolean cleared = rs.acknowledgeAnomaly();
        if (cleared) {
            AnomalyDetector detector = engine.getAnomalyDetector();
            if (detector != null) detector.reset();
            return new ActionResponse(true,
                    "Anomaly acknowledged and cleared. Use POST /api/reset to resume trading.");
        }
        return new ActionResponse(false, "No active anomaly to acknowledge");
    }

    @PostMapping("/emergency-flatten")
    public ActionResponse emergencyFlatten(
            @RequestParam(defaultValue = "Manual emergency flatten via REST") String reason) {
        int closed = engine.flattenAll(reason);
        return new ActionResponse(true,
                "Emergency flatten complete: " + closed + " positions closed. Anomaly mode active.");
    }

    @PostMapping("/kill")
    public ActionResponse kill(@RequestParam(defaultValue = "Manual kill via REST API") String reason) {
        engine.getRiskSessionState().activateKillSwitch(reason);
        return new ActionResponse(true, "Kill switch activated: " + reason);
    }

    @PostMapping("/reset")
    public ActionResponse reset() {
        engine.getRiskSessionState().resetDay();
        return new ActionResponse(true, "Daily risk state reset");
    }
}
