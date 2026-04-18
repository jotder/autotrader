package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.config.DimensionDataCache;
import com.rj.config.MarketCategory;
import com.rj.config.SymbolFormatParser;
import com.rj.config.SymbolMasterCache;
import com.rj.config.SymbolRegistry;
import com.rj.model.ParsedSymbol;
import com.rj.model.dim.SymbolMasterEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class SymbolController {

    private final ConfigManager configManager;
    private final DimensionDataCache dimensionCache;
    private final SymbolMasterCache symbolMasterCache;

    public SymbolController(ConfigManager configManager,
                            DimensionDataCache dimensionCache,
                            SymbolMasterCache symbolMasterCache) {
        this.configManager = configManager;
        this.dimensionCache = dimensionCache;
        this.symbolMasterCache = symbolMasterCache;
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
}
