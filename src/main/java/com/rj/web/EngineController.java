package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.config.DimensionDataCache;
import com.rj.config.MarketCategory;
import com.rj.config.SymbolFormatParser;
import com.rj.config.SymbolMasterCache;
import com.rj.config.SymbolRegistry;
import com.rj.engine.*;
import com.rj.model.*;
import com.rj.model.dim.SymbolMasterEntry;
import com.rj.web.dto.ActionResponse;
import com.rj.web.dto.RiskResponse;
import com.rj.web.dto.StatusResponse;
import com.rj.web.dto.TickResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class EngineController {

    private final TradingEngine engine;
    private final TickStore tickStore;
    private final ConfigManager configManager;
    private final DimensionDataCache dimensionCache;
    private final SymbolMasterCache symbolMasterCache;
    private final CandleDatabase candleDatabase;
    private final SymbolProfiler symbolProfiler;

    public EngineController(TradingEngine engine, TickStore tickStore, ConfigManager configManager,
                            DimensionDataCache dimensionCache, SymbolMasterCache symbolMasterCache,
                            CandleDatabase candleDatabase, SymbolProfiler symbolProfiler) {
        this.engine = engine;
        this.tickStore = tickStore;
        this.configManager = configManager;
        this.dimensionCache = dimensionCache;
        this.symbolMasterCache = symbolMasterCache;
        this.candleDatabase = candleDatabase;
        this.symbolProfiler = symbolProfiler;
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

    @GetMapping("/symbols")
    public Map<String, Object> symbols() {
        SymbolRegistry reg = configManager.getSymbolRegistry();
        if (reg == null) {
            return Map.of("error", "Symbol registry not loaded",
                    "symbols", List.of(configManager.getActiveSymbols()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (MarketCategory cat : MarketCategory.values()) {
            result.put(cat.yamlKey(), reg.symbolsFor(cat));
        }
        result.put("total", reg.size());
        return result;
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

    // ── Dimension & Symbol Master endpoints ───────────────────────────────

    @GetMapping("/dimensions")
    public Map<String, Object> dimensions() {
        return dimensionCache.allTables();
    }

    @GetMapping("/dimensions/{table}")
    public ResponseEntity<?> dimensionTable(@PathVariable String table) {
        return dimensionCache.tableByName(table)
                .map(list -> ResponseEntity.ok((Object) list))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/symbol-master")
    public ResponseEntity<?> symbolMaster(
            @RequestParam(required = false) Integer exchange,
            @RequestParam(required = false) Integer segment,
            @RequestParam(required = false) String underlying,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit) {

        // Exact ticker lookup
        if (ticker != null && !ticker.isBlank()) {
            return symbolMasterCache.byTicker(ticker)
                    .map(e -> ResponseEntity.ok((Object) e))
                    .orElse(ResponseEntity.notFound().build());
        }

        // Search by query
        if (q != null && !q.isBlank()) {
            return ResponseEntity.ok(symbolMasterCache.search(q, limit));
        }

        // Filter by underlying
        if (underlying != null && !underlying.isBlank()) {
            List<SymbolMasterEntry> results = symbolMasterCache.byUnderlying(underlying);
            return ResponseEntity.ok(results.isEmpty() ? List.of() : results);
        }

        // Filter by exchange + segment
        if (exchange != null && segment != null) {
            return ResponseEntity.ok(symbolMasterCache.byExchangeSegment(exchange, segment));
        }

        // Default: return summary
        return ResponseEntity.ok(Map.of(
                "totalSymbols", symbolMasterCache.size(),
                "underlyings", symbolMasterCache.allUnderlyings().size(),
                "hint", "Use ?ticker=NSE:SBIN-EQ, ?underlying=NIFTY, ?exchange=10&segment=11, or ?q=SBIN"));
    }

    // ── Symbol parsing endpoint ───────────────────────────────────────────

    @GetMapping("/symbol/parse")
    public ResponseEntity<?> parseSymbol(@RequestParam("s") String symbol) {
        ParsedSymbol parsed = SymbolFormatParser.parse(symbol);
        if (parsed == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unrecognized symbol format",
                    "symbol", symbol));
        }
        return ResponseEntity.ok(parsed);
    }

    // ── Candle Database endpoints ─────────────────────────────────────────

    @GetMapping("/candle-db/symbols")
    public Set<String> candleDbSymbols() {
        return candleDatabase.availableSymbols();
    }

    @GetMapping("/candle-db/{symbol}/dates")
    public List<LocalDate> candleDbDates(@PathVariable String symbol) {
        return candleDatabase.availableDates(symbol);
    }

    @GetMapping("/candle-db/{symbol}")
    public ResponseEntity<?> candleDbLoad(@PathVariable String symbol,
                                          @RequestParam String date) {
        LocalDate d = LocalDate.parse(date);
        List<Candle> candles = candleDatabase.load(symbol, d);
        if (candles.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "symbol", symbol,
                "date", date,
                "count", candles.size(),
                "candles", candles));
    }

    // ── Backtest endpoint ─────────────────────────────────────────────────

    @PostMapping("/backtest")
    public ResponseEntity<?> backtest(@RequestBody Map<String, String> request) {
        String symbol = request.get("symbol");
        String fromStr = request.get("from");
        String toStr = request.get("to");

        if (symbol == null || fromStr == null || toStr == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Required fields: symbol, from, to"));
        }

        LocalDate from = LocalDate.parse(fromStr);
        LocalDate to = LocalDate.parse(toStr);

        List<Candle> m1Candles = candleDatabase.loadRange(symbol, from, to);
        if (m1Candles.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No M1 data found for " + symbol + " in range " + from + " to " + to,
                    "hint", "Download data first via POST /api/candle-db/download"));
        }

        BacktestEngine bt = BacktestEngine.fromM1(m1Candles, symbol, configManager.getRiskConfig());
        StrategyAnalyzer.Report report = bt.run();
        return ResponseEntity.ok(report);
    }

    // ── Symbol Profile endpoint ───────────────────────────────────────────

    @GetMapping("/profile/{symbol}")
    public ResponseEntity<?> profile(@PathVariable String symbol,
                                     @RequestParam String from,
                                     @RequestParam String to) {
        SymbolProfile profile = symbolProfiler.profile(symbol, LocalDate.parse(from), LocalDate.parse(to));
        if (profile == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Insufficient data for profiling " + symbol));
        }
        return ResponseEntity.ok(profile);
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
}
