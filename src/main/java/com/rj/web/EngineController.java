package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.engine.PositionMonitor;
import com.rj.engine.RiskManager;
import com.rj.engine.StrategyAnalyzer;
import com.rj.engine.TradingEngine;
import com.rj.model.OpenPosition;
import com.rj.model.Tick;
import com.rj.model.TickBuffer;
import com.rj.model.TickStore;
import com.rj.model.TradeRecord;
import com.rj.web.dto.ActionResponse;
import com.rj.web.dto.RiskResponse;
import com.rj.web.dto.StatusResponse;
import com.rj.web.dto.TickResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse(
                engine.isRunning(),
                engine.getMode().name(),
                List.of(configManager.getActiveSymbols()),
                Instant.now()
        );
    }

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

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "engineRunning", engine.isRunning(),
                "positionMonitorRunning", engine.getPositionMonitor().isRunning(),
                "healthMonitorRunning", engine.getHealthMonitor().isRunning(),
                "openPositionCount", engine.getPositionMonitor().openPositionCount(),
                "closedTradeCount", engine.getJournal().closedTradeCount(),
                "timestamp", Instant.now()
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
}
