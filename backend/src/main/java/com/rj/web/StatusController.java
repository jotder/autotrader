package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.engine.TradingEngine;
import com.rj.web.dto.StatusResponse;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StatusController {

    private final TradingEngine engine;
    private final ConfigManager configManager;

    public StatusController(TradingEngine engine, ConfigManager configManager) {
        this.engine = engine;
        this.configManager = configManager;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse(
                engine.isRunning(),
                engine.getMode().name(),
                List.of(configManager.getActiveSymbols()),
                Instant.now()
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "engineRunning", engine.isRunning(),
                "scheduledPositionManagerRunning", engine.getScheduledPositionManager().isRunning(),
                "healthMonitorRunning", engine.getHealthMonitor().isRunning(),
                "openPositionCount", engine.getPositionBook().openPositionCount(),
                "closedTradeCount", engine.getJournal().closedTradeCount(),
                "timestamp", Instant.now()
        );
    }
}
