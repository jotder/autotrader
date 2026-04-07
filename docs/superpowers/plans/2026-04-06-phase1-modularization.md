# Phase 1 Modularization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split three god-objects — `EngineController` (80+ endpoints), `ConfigManager` (static singleton), and `BacktestController`/`RiskController` inconsistencies — into focused, single-responsibility classes with no behaviour change.

**Architecture:** Each new controller owns exactly one domain (status, symbols, candles); `ConfigManager` loses its static singleton and becomes a Spring `@Component`; `EnvConfigPersistence` is extracted to own `.env` write operations. All splits are pure reorganisation — no business logic changes.

**Tech Stack:** Java 25, Spring Boot 3.4.4, JUnit 5, Spring MockMvc (`@WebMvcTest`), Mockito.

---

## File Map

### New files
| File | Responsibility |
|------|---------------|
| `src/main/java/com/rj/web/StatusController.java` | `GET /api/status`, `GET /api/health` |
| `src/main/java/com/rj/web/SymbolController.java` | `/api/symbols`, `/api/symbol/**`, `/api/symbol-master`, `/api/dimensions/**` |
| `src/main/java/com/rj/web/CandleController.java` | `/api/candle-db/**`, `GET /api/profile/{symbol}` |
| `src/main/java/com/rj/config/EnvConfigPersistence.java` | `.env` file write operations only |
| `src/test/java/com/rj/web/StatusControllerTest.java` | MockMvc tests for StatusController |
| `src/test/java/com/rj/web/SymbolControllerTest.java` | MockMvc tests for SymbolController |
| `src/test/java/com/rj/web/CandleControllerTest.java` | MockMvc tests for CandleController |

### Modified files
| File | Change |
|------|--------|
| `src/main/java/com/rj/web/EngineController.java` | Remove migrated endpoints; keep only trading ops (positions, trades, orders, ticks, kill/reset/exit, reconciliation, token, anomaly, circuit-breaker, emergency-flatten, metrics) |
| `src/main/java/com/rj/web/RiskController.java` | Add `GET /api/risk` and `GET /api/anomaly/status`, `POST /api/anomaly/acknowledge`, `POST /api/emergency-flatten` moved from EngineController |
| `src/main/java/com/rj/web/BacktestController.java` | Add `POST /api/backtest` (simple one-shot backtest) moved from EngineController |
| `src/main/java/com/rj/config/EngineConfiguration.java` | Add `@Bean` for `BrokerCircuitBreaker`, `CandleDownloader`, `DownloadTracker` |
| `src/main/java/com/rj/config/ConfigManager.java` | Remove static singleton (`manager` field + `getInstance()`); add `@Component` + `@PostConstruct` |
| `src/main/java/com/rj/web/BacktestController.java` | Replace `ConfigManager.getInstance()` with injected `ConfigManager` |
| `src/test/java/com/rj/web/StrategyControllerTest.java` | Fix broken test — add `@MockBean` annotations |

---

## Task 1: Expose BrokerCircuitBreaker and DownloadTracker as Spring beans

`EngineController` currently builds `DownloadTracker` lazily via a double-checked lock, pulling `engine.getCircuitBreaker()` inline. This blocks the controller split. Registering both as beans removes the coupling.

**Files:**
- Modify: `src/main/java/com/rj/config/EngineConfiguration.java`

- [ ] **Step 1: Add three new `@Bean` methods to `EngineConfiguration`**

Open `src/main/java/com/rj/config/EngineConfiguration.java` and add after the `symbolProfiler` bean (before the closing brace):

```java
@Bean
public com.rj.engine.BrokerCircuitBreaker brokerCircuitBreaker(TradingEngine tradingEngine) {
    return tradingEngine.getCircuitBreaker();
}

@Bean
public com.rj.engine.CandleDownloader candleDownloader(
        com.rj.engine.CandleDatabase candleDatabase,
        com.rj.engine.BrokerCircuitBreaker circuitBreaker) {
    return new com.rj.engine.CandleDownloader(
            new com.rj.fyers.FyersDataApi(), candleDatabase, 500, circuitBreaker);
}

@Bean
public com.rj.engine.DownloadTracker downloadTracker(com.rj.engine.CandleDownloader candleDownloader) {
    return new com.rj.engine.DownloadTracker(candleDownloader);
}
```

- [ ] **Step 2: Verify the application context loads**

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.main.banner-mode=off" 2>&1 | head -40
```

Expected: no `NoSuchBeanDefinitionException`. Stop with Ctrl-C after context starts.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/rj/config/EngineConfiguration.java
git commit -m "feat(config): expose BrokerCircuitBreaker and DownloadTracker as Spring beans"
```

---

## Task 2: Create StatusController

Extract `GET /api/status` and `GET /api/health` from `EngineController`. These two endpoints have no shared state with the rest of `EngineController` — they only need `TradingEngine` + `ConfigManager`.

**Files:**
- Create: `src/main/java/com/rj/web/StatusController.java`
- Create: `src/test/java/com/rj/web/StatusControllerTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/rj/web/StatusControllerTest.java`:

```java
package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.engine.HealthMonitor;
import com.rj.engine.PositionMonitor;
import com.rj.engine.TradingEngine;
import com.rj.engine.TradeJournal;
import com.rj.model.ExecutionMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatusController.class)
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingEngine engine;

    @MockBean
    private ConfigManager configManager;

    @Test
    void status_returnsEngineState() throws Exception {
        when(engine.isRunning()).thenReturn(true);
        when(engine.getMode()).thenReturn(ExecutionMode.PAPER);
        when(configManager.getActiveSymbols()).thenReturn(new String[]{"NSE:NIFTY50-INDEX"});

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.mode").value("PAPER"))
                .andExpect(jsonPath("$.activeSymbols[0]").value("NSE:NIFTY50-INDEX"));
    }

    @Test
    void health_returnsComponentStatus() throws Exception {
        PositionMonitor pm = org.mockito.Mockito.mock(PositionMonitor.class);
        HealthMonitor hm = org.mockito.Mockito.mock(HealthMonitor.class);
        TradeJournal journal = org.mockito.Mockito.mock(TradeJournal.class);

        when(engine.isRunning()).thenReturn(true);
        when(engine.getPositionMonitor()).thenReturn(pm);
        when(engine.getHealthMonitor()).thenReturn(hm);
        when(engine.getJournal()).thenReturn(journal);
        when(pm.isRunning()).thenReturn(true);
        when(hm.isRunning()).thenReturn(true);
        when(pm.openPositionCount()).thenReturn(2);
        when(journal.closedTradeCount()).thenReturn(5);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineRunning").value(true))
                .andExpect(jsonPath("$.positionMonitorRunning").value(true))
                .andExpect(jsonPath("$.openPositionCount").value(2))
                .andExpect(jsonPath("$.closedTradeCount").value(5));
    }
}
```

- [ ] **Step 2: Run the test — it must fail (class not found)**

```bash
./mvnw test -pl . -Dtest=StatusControllerTest -q 2>&1 | tail -20
```

Expected: `COMPILATION ERROR` — `StatusController` does not exist yet.

- [ ] **Step 3: Create StatusController**

Create `src/main/java/com/rj/web/StatusController.java`:

```java
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
                "positionMonitorRunning", engine.getPositionMonitor().isRunning(),
                "healthMonitorRunning", engine.getHealthMonitor().isRunning(),
                "openPositionCount", engine.getPositionMonitor().openPositionCount(),
                "closedTradeCount", engine.getJournal().closedTradeCount(),
                "timestamp", Instant.now()
        );
    }
}
```

- [ ] **Step 4: Run the test — it must pass**

```bash
./mvnw test -pl . -Dtest=StatusControllerTest -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 5: Remove the two endpoints from EngineController**

In `src/main/java/com/rj/web/EngineController.java`, delete the `status()` method (lines 66–74) and the `health()` method (lines 121–130), including their `@GetMapping` annotations.

- [ ] **Step 6: Run all tests to confirm nothing broke**

```bash
./mvnw test -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/rj/web/StatusController.java \
        src/main/java/com/rj/web/EngineController.java \
        src/test/java/com/rj/web/StatusControllerTest.java
git commit -m "feat(web): extract StatusController from EngineController"
```

---

## Task 3: Create SymbolController

Extract the five symbol/dimension endpoints from `EngineController`. These share no state with trading operations.

**Files:**
- Create: `src/main/java/com/rj/web/SymbolController.java`
- Create: `src/test/java/com/rj/web/SymbolControllerTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/rj/web/SymbolControllerTest.java`:

```java
package com.rj.web;

import com.rj.config.*;
import com.rj.model.dim.SymbolMasterEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SymbolController.class)
class SymbolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConfigManager configManager;

    @MockBean
    private DimensionDataCache dimensionCache;

    @MockBean
    private SymbolMasterCache symbolMasterCache;

    @Test
    void symbols_returnsRegistryContents() throws Exception {
        SymbolRegistry registry = org.mockito.Mockito.mock(SymbolRegistry.class);
        when(configManager.getSymbolRegistry()).thenReturn(registry);
        when(registry.symbolsFor(MarketCategory.NSE_EQ)).thenReturn(List.of("NSE:SBIN-EQ"));
        when(registry.size()).thenReturn(1);

        mockMvc.perform(get("/api/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void symbolMaster_summary_whenNoParams() throws Exception {
        when(symbolMasterCache.size()).thenReturn(42);
        when(symbolMasterCache.allUnderlyings()).thenReturn(List.of("NIFTY", "SBIN"));

        mockMvc.perform(get("/api/symbol-master"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSymbols").value(42));
    }

    @Test
    void symbolMaster_byTicker_found() throws Exception {
        SymbolMasterEntry entry = org.mockito.Mockito.mock(SymbolMasterEntry.class);
        when(symbolMasterCache.byTicker("NSE:SBIN-EQ")).thenReturn(Optional.of(entry));

        mockMvc.perform(get("/api/symbol-master").param("ticker", "NSE:SBIN-EQ"))
                .andExpect(status().isOk());
    }

    @Test
    void symbolMaster_byTicker_notFound() throws Exception {
        when(symbolMasterCache.byTicker("NSE:UNKNOWN-EQ")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/symbol-master").param("ticker", "NSE:UNKNOWN-EQ"))
                .andExpect(status().isNotFound());
    }

    @Test
    void parseSymbol_invalidFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/symbol/parse").param("s", "GARBAGE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dimensions_returnsAllTables() throws Exception {
        when(dimensionCache.allTables()).thenReturn(Map.of("exchanges", List.of()));

        mockMvc.perform(get("/api/dimensions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchanges").isArray());
    }

    @Test
    void dimensionTable_notFound_returns404() throws Exception {
        when(dimensionCache.tableByName("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/dimensions/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run the test — expect compilation failure**

```bash
./mvnw test -pl . -Dtest=SymbolControllerTest -q 2>&1 | tail -10
```

Expected: `COMPILATION ERROR`.

- [ ] **Step 3: Create SymbolController**

Create `src/main/java/com/rj/web/SymbolController.java`:

```java
package com.rj.web;

import com.rj.config.*;
import com.rj.model.dim.SymbolMasterEntry;
import com.rj.model.ParsedSymbol;
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

    @GetMapping("/symbol-master")
    public ResponseEntity<?> symbolMaster(
            @RequestParam(required = false) Integer exchange,
            @RequestParam(required = false) Integer segment,
            @RequestParam(required = false) String underlying,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit) {

        if (ticker != null && !ticker.isBlank()) {
            return symbolMasterCache.byTicker(ticker)
                    .map(e -> ResponseEntity.ok((Object) e))
                    .orElse(ResponseEntity.notFound().build());
        }
        if (q != null && !q.isBlank()) {
            return ResponseEntity.ok(symbolMasterCache.search(q, limit));
        }
        if (underlying != null && !underlying.isBlank()) {
            List<SymbolMasterEntry> results = symbolMasterCache.byUnderlying(underlying);
            return ResponseEntity.ok(results.isEmpty() ? List.of() : results);
        }
        if (exchange != null && segment != null) {
            return ResponseEntity.ok(symbolMasterCache.byExchangeSegment(exchange, segment));
        }
        return ResponseEntity.ok(Map.of(
                "totalSymbols", symbolMasterCache.size(),
                "underlyings", symbolMasterCache.allUnderlyings().size(),
                "hint", "Use ?ticker=NSE:SBIN-EQ, ?underlying=NIFTY, ?exchange=10&segment=11, or ?q=SBIN"));
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
```

- [ ] **Step 4: Run tests — all must pass**

```bash
./mvnw test -pl . -Dtest=SymbolControllerTest -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, 7 tests passed.

- [ ] **Step 5: Remove the five endpoints from EngineController**

In `src/main/java/com/rj/web/EngineController.java`, delete:
- `symbols()` method and its `@GetMapping("/symbols")`
- `symbolMaster()` method and its `@GetMapping("/symbol-master")`
- `parseSymbol()` method and its `@GetMapping("/symbol/parse")`
- `dimensions()` method and its `@GetMapping("/dimensions")`
- `dimensionTable()` method and its `@GetMapping("/dimensions/{table}")`

Also remove the now-unused imports: `SymbolFormatParser`, `MarketCategory`, `SymbolMasterCache`, `DimensionDataCache` from `EngineController.java`, and remove those constructor parameters and fields.

- [ ] **Step 6: Run all tests**

```bash
./mvnw test -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/rj/web/SymbolController.java \
        src/main/java/com/rj/web/EngineController.java \
        src/test/java/com/rj/web/SymbolControllerTest.java
git commit -m "feat(web): extract SymbolController from EngineController"
```

---

## Task 4: Create CandleController

Extract all `/api/candle-db/**` and `/api/profile/{symbol}` endpoints from `EngineController`. These now use the `DownloadTracker` bean from Task 1 directly.

**Files:**
- Create: `src/main/java/com/rj/web/CandleController.java`
- Create: `src/test/java/com/rj/web/CandleControllerTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/rj/web/CandleControllerTest.java`:

```java
package com.rj.web;

import com.rj.engine.CandleDatabase;
import com.rj.engine.DownloadTracker;
import com.rj.engine.SymbolProfiler;
import com.rj.model.Candle;
import com.rj.model.SymbolProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CandleController.class)
class CandleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CandleDatabase candleDatabase;

    @MockBean
    private DownloadTracker downloadTracker;

    @MockBean
    private SymbolProfiler symbolProfiler;

    @Test
    void candleDbSymbols_returnsSet() throws Exception {
        when(candleDatabase.availableSymbols()).thenReturn(Set.of("NSE:SBIN-EQ"));

        mockMvc.perform(get("/api/candle-db/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("NSE:SBIN-EQ"));
    }

    @Test
    void candleDbLoad_notFound_returns404() throws Exception {
        when(candleDatabase.load(eq("NSE:SBIN-EQ"), any(LocalDate.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/candle-db/NSE:SBIN-EQ").param("date", "2026-01-02"))
                .andExpect(status().isNotFound());
    }

    @Test
    void candleDbLoad_found_returnsCandles() throws Exception {
        Candle c = org.mockito.Mockito.mock(Candle.class);
        when(candleDatabase.load(eq("NSE:SBIN-EQ"), any(LocalDate.class)))
                .thenReturn(List.of(c));

        mockMvc.perform(get("/api/candle-db/NSE:SBIN-EQ").param("date", "2026-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void download_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/candle-db/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadStatus_notFound_returns404() throws Exception {
        when(downloadTracker.getJob("unknown-job")).thenReturn(null);

        mockMvc.perform(get("/api/candle-db/download/unknown-job"))
                .andExpect(status().isNotFound());
    }

    @Test
    void profile_noData_returns400() throws Exception {
        when(symbolProfiler.profile(eq("NSE:SBIN-EQ"), any(), any()))
                .thenReturn(null);

        mockMvc.perform(get("/api/profile/NSE:SBIN-EQ")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profile_found_returns200() throws Exception {
        SymbolProfile profile = org.mockito.Mockito.mock(SymbolProfile.class);
        when(symbolProfiler.profile(eq("NSE:SBIN-EQ"), any(), any()))
                .thenReturn(profile);

        mockMvc.perform(get("/api/profile/NSE:SBIN-EQ")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31"))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./mvnw test -pl . -Dtest=CandleControllerTest -q 2>&1 | tail -10
```

Expected: `COMPILATION ERROR`.

- [ ] **Step 3: Create CandleController**

Create `src/main/java/com/rj/web/CandleController.java`:

```java
package com.rj.web;

import com.rj.engine.CandleDatabase;
import com.rj.engine.DownloadTracker;
import com.rj.engine.SymbolProfiler;
import com.rj.model.Candle;
import com.rj.model.SymbolProfile;
import com.rj.web.dto.ActionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class CandleController {

    private final CandleDatabase candleDatabase;
    private final DownloadTracker downloadTracker;
    private final SymbolProfiler symbolProfiler;

    public CandleController(CandleDatabase candleDatabase,
                            DownloadTracker downloadTracker,
                            SymbolProfiler symbolProfiler) {
        this.candleDatabase = candleDatabase;
        this.downloadTracker = downloadTracker;
        this.symbolProfiler = symbolProfiler;
    }

    @GetMapping("/candle-db/symbols")
    public Set<String> candleDbSymbols() {
        return candleDatabase.availableSymbols();
    }

    @GetMapping("/candle-db/summary")
    public List<Map<String, Object>> candleDbSummary() {
        var result = new ArrayList<Map<String, Object>>();
        for (String s : candleDatabase.availableSymbols()) {
            var dates = candleDatabase.availableDates(s);
            if (!dates.isEmpty()) {
                result.add(Map.of(
                        "symbol", s,
                        "startDate", dates.getFirst().toString(),
                        "endDate", dates.getLast().toString(),
                        "count", dates.size()
                ));
            }
        }
        return result;
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
        if (candles.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of(
                "symbol", symbol,
                "date", date,
                "count", candles.size(),
                "candles", candles));
    }

    @PostMapping("/candle-db/download")
    public ResponseEntity<?> candleDbDownload(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> symbols = (List<String>) request.get("symbols");
        String fromStr = (String) request.get("from");
        String toStr   = (String) request.get("to");

        if (symbols == null || symbols.isEmpty() || fromStr == null || toStr == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Required: symbols (list), from (date), to (date)"));
        }

        LocalDate from = LocalDate.parse(fromStr);
        LocalDate to   = LocalDate.parse(toStr);
        String jobId   = downloadTracker.startJob(symbols, from, to);

        return ResponseEntity.accepted().body(Map.of(
                "jobId", jobId,
                "status", "RUNNING",
                "message", "Download started for " + symbols.size() + " symbols",
                "checkUrl", "/api/candle-db/download/" + jobId));
    }

    @GetMapping("/candle-db/download/{jobId}")
    public ResponseEntity<?> candleDbDownloadStatus(@PathVariable String jobId) {
        var job = downloadTracker.getJob(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(job.toMap());
    }

    @GetMapping("/candle-db/downloads")
    public List<Map<String, Object>> candleDbDownloads() {
        return downloadTracker.allJobs().stream()
                .map(DownloadTracker.DownloadJob::toMap)
                .toList();
    }

    @GetMapping("/profile/{symbol}")
    public ResponseEntity<?> profile(@PathVariable String symbol,
                                     @RequestParam String from,
                                     @RequestParam String to) {
        SymbolProfile profile = symbolProfiler.profile(
                symbol, LocalDate.parse(from), LocalDate.parse(to));
        if (profile == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Insufficient data for profiling " + symbol));
        }
        return ResponseEntity.ok(profile);
    }
}
```

- [ ] **Step 4: Run tests — all must pass**

```bash
./mvnw test -pl . -Dtest=CandleControllerTest -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, 7 tests passed.

- [ ] **Step 5: Remove candle + profile endpoints from EngineController**

In `src/main/java/com/rj/web/EngineController.java`, delete:
- `candleDbSymbols()`, `candleDbSummary()`, `candleDbDates()`, `candleDbLoad()` methods
- `candleDbDownload()`, `candleDbDownloadStatus()`, `candleDbDownloads()` methods
- `profile()` method
- The `getDownloadTracker()` private helper method
- The `volatile DownloadTracker downloadTracker` field
- The `candleDatabase` field, constructor parameter, and `CandleDatabase` import
- The `symbolProfiler` field, constructor parameter, and `SymbolProfiler` import

- [ ] **Step 6: Run all tests**

```bash
./mvnw test -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/rj/web/CandleController.java \
        src/main/java/com/rj/web/EngineController.java \
        src/test/java/com/rj/web/CandleControllerTest.java
git commit -m "feat(web): extract CandleController from EngineController"
```

---

## Task 5: Redistribute remaining misplaced endpoints

Two groups of endpoints currently in `EngineController` belong in existing controllers: the `/api/risk` GET and `/api/anomaly/**` and `/api/emergency-flatten` belong in `RiskController`; the simple `/api/backtest` POST belongs in `BacktestController`.

**Files:**
- Modify: `src/main/java/com/rj/web/RiskController.java`
- Modify: `src/main/java/com/rj/web/BacktestController.java`
- Modify: `src/main/java/com/rj/web/EngineController.java`

- [ ] **Step 1: Move risk + anomaly endpoints to RiskController**

Replace the full contents of `src/main/java/com/rj/web/RiskController.java` with:

```java
package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.engine.AnomalyDetector;
import com.rj.engine.RiskManager;
import com.rj.engine.TradingEngine;
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

        RiskManager.PreTradeResult result = engine.getRiskManager().preTradeCheck(
                dummySignal,
                engine.getPositionMonitor().openPositions(),
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
        RiskManager rm = engine.getRiskManager();
        var result = new LinkedHashMap<String, Object>();
        result.put("anomalyMode", rm.isAnomalyMode());
        result.put("reason", rm.getAnomalyReason());
        result.put("triggeredAt", rm.getAnomalyTriggeredAt());
        result.put("killSwitchActive", rm.isKillSwitchActive());
        AnomalyDetector detector = engine.getAnomalyDetector();
        if (detector != null) {
            result.put("detectorTriggered", detector.isTriggered());
            result.put("consecutiveBrokerErrors", detector.getConsecutiveBrokerErrors());
        }
        return result;
    }

    @PostMapping("/anomaly/acknowledge")
    public ActionResponse acknowledgeAnomaly() {
        RiskManager rm = engine.getRiskManager();
        boolean cleared = rm.acknowledgeAnomaly();
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
}
```

- [ ] **Step 2: Move simple backtest endpoint to BacktestController**

In `src/main/java/com/rj/web/BacktestController.java`, add these imports at the top:

```java
import com.rj.config.RiskConfig;
import com.rj.engine.BacktestEngine;
import com.rj.engine.StrategyAnalyzer;
import com.rj.model.Candle;
import com.rj.strategy.MultiTimeframeVotingStrategy;
```

Then add the following method to the class body (before the closing `}`), and add `RiskConfig riskConfig` to the constructor:

```java
// Add riskConfig field:
private final RiskConfig riskConfig;

// Update constructor to:
public BacktestController(BacktestService backtestService, CandleDatabase candleDatabase,
                          RiskConfig riskConfig) {
    this.backtestService = backtestService;
    this.candleDatabase = candleDatabase;
    this.riskConfig = riskConfig;
}

// Add this method:
@PostMapping
public ResponseEntity<?> backtest(@RequestBody Map<String, String> request) {
    String symbol  = request.get("symbol");
    String fromStr = request.get("from");
    String toStr   = request.get("to");

    if (symbol == null || fromStr == null || toStr == null) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Required fields: symbol, from, to"));
    }

    List<Candle> m1Candles = candleDatabase.loadRange(
            symbol, LocalDate.parse(fromStr), LocalDate.parse(toStr));
    if (m1Candles.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "No M1 data found for " + symbol + " in range " + fromStr + " to " + toStr,
                "hint", "Download data first via POST /api/candle-db/download"));
    }

    List<Candle> m5Candles = BacktestEngine.aggregateToHigherTimeframe(m1Candles, 5);
    if (m5Candles.isEmpty()) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Could not aggregate M1 data into M5 candles"));
    }

    var strategy = new MultiTimeframeVotingStrategy("MTF-VOTE", "Multi-TF Voting", 0.6, 1.5, 2.0);
    StrategyAnalyzer.Report report =
            new BacktestEngine(m5Candles, symbol, strategy, riskConfig).run();
    return ResponseEntity.ok(report);
}
```

Note: The URL is now `POST /api/backtest` (matching existing `@RequestMapping("/api/backtest")`). Also remove `ConfigManager.getInstance()` call — `riskConfig` is injected.

- [ ] **Step 3: Remove the three endpoint groups from EngineController**

In `src/main/java/com/rj/web/EngineController.java`, delete:
- `risk()` method (`@GetMapping("/risk")`)
- `anomalyStatus()` method (`@GetMapping("/anomaly/status")`)
- `acknowledgeAnomaly()` method (`@PostMapping("/anomaly/acknowledge")`)
- `emergencyFlatten()` method (`@PostMapping("/emergency-flatten")`)
- `backtest()` method (`@PostMapping("/backtest")`)
- Unused imports: `MultiTimeframeVotingStrategy`, `BacktestEngine`

- [ ] **Step 4: Run all tests**

```bash
./mvnw test -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/rj/web/RiskController.java \
        src/main/java/com/rj/web/BacktestController.java \
        src/main/java/com/rj/web/EngineController.java
git commit -m "refactor(web): consolidate risk/anomaly into RiskController, backtest into BacktestController"
```

---

## Task 6: Remove ConfigManager static singleton

`ConfigManager` is currently a hand-rolled singleton with a static `getInstance()` method. `EngineConfiguration` already registers it as a Spring `@Bean` via `getInstance()`, but callers like `RiskController` and `BacktestController` bypass Spring and call `ConfigManager.getInstance()` directly. This task converts `ConfigManager` to a proper Spring `@Component`.

**Files:**
- Modify: `src/main/java/com/rj/config/ConfigManager.java`
- Modify: `src/main/java/com/rj/config/EngineConfiguration.java`
- Modify: `src/main/java/com/rj/web/RiskController.java` (already fixed in Task 5 — verify no `getInstance()`)
- Modify: `src/main/java/com/rj/web/BacktestController.java` (already fixed in Task 5 — verify no `getInstance()`)

- [ ] **Step 1: Verify all `getInstance()` call sites**

```bash
grep -rn "ConfigManager.getInstance()" src/
```

Expected output includes only `EngineConfiguration.java` and possibly `TradingEngine.java`. Fix any other callers by injecting `ConfigManager` via constructor instead.

- [ ] **Step 2: Convert ConfigManager to a Spring @Component**

Replace the full contents of `src/main/java/com/rj/config/ConfigManager.java` with:

```java
package com.rj.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ConfigManager implements IConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final Path SYMBOLS_YAML_PATH = Path.of("config/symbols.yaml");
    private static final String[] REQUIRED_KEYS = {"FYERS_APP_ID", "FYERS_SECRET_KEY",
            "FYERS_REDIRECT_URI", "FYERS_AUTH_CODE", "APP_ENV", "LOG_LEVEL"};

    private Dotenv dotenv;
    private String[] activeSymbols = {"NSE:NIFTY50-INDEX"};
    private Set<String> activeSymbolSet = new LinkedHashSet<>(Arrays.asList(activeSymbols));
    private RiskConfig riskConfig = RiskConfig.defaults();
    private StrategyConfig strategyConfig = StrategyConfig.defaults();
    private SymbolRegistry symbolRegistry;

    @PostConstruct
    @Override
    public void loadConfiguration() {
        log.info("Loading system configuration from .env...");
        try {
            this.dotenv = Dotenv.configure().ignoreIfMissing().load();
            log.info("Configuration loaded. APP_ENV: {}", getProperty("APP_ENV"));

            if (Files.exists(SYMBOLS_YAML_PATH)) {
                symbolRegistry = SymbolRegistry.load(SYMBOLS_YAML_PATH);
                activeSymbols = symbolRegistry.allSymbols();
                activeSymbolSet = new LinkedHashSet<>(Arrays.asList(activeSymbols));
                log.info("Symbol registry loaded: {} symbols", symbolRegistry.size());
            } else {
                log.warn("config/symbols.yaml not found — falling back to .env FYERS_SYMBOLS (deprecated)");
                String symbolsEnv = getProperty("FYERS_SYMBOLS");
                if (symbolsEnv != null && !symbolsEnv.isBlank()) {
                    String[] parsedSymbols = Arrays.stream(symbolsEnv.split(","))
                            .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
                    if (parsedSymbols.length > 0) activeSymbols = parsedSymbols;
                }
                activeSymbolSet = new LinkedHashSet<>(Arrays.asList(activeSymbols));
            }

            riskConfig = RiskConfig.fromEnvironment(this::getProperty);
            strategyConfig = StrategyConfig.fromEnvironment(this::getProperty);
            log.info("Active symbols: {}", String.join(", ", activeSymbols));
        } catch (Exception e) {
            log.error("Failed to load .env file", e);
        }
    }

    @Override
    public String getProperty(String key) {
        return dotenv != null ? dotenv.get(key) : null;
    }

    @Override
    public boolean validateRequiredConfiguration() {
        Set<String> missing = new LinkedHashSet<>();
        for (String key : REQUIRED_KEYS) {
            String value = getProperty(key);
            if (value == null || value.isBlank()) missing.add(key);
        }
        if (!missing.isEmpty()) {
            log.error("Missing required keys: {}", String.join(", ", missing));
            return false;
        }
        return true;
    }

    @Override
    public String[] getActiveSymbols() { return activeSymbols; }

    @Override
    public boolean isSymbolActive(String symbol) {
        if (symbolRegistry != null) return symbolRegistry.contains(symbol);
        return symbol != null && activeSymbolSet.contains(symbol.trim());
    }

    @Override
    public SymbolRegistry getSymbolRegistry() { return symbolRegistry; }

    @Override
    public String getActiveStrategy(String symbol) {
        String override = getProperty("STRATEGY_DEFAULT");
        return override != null ? override : "ORB_15M";
    }

    @Override
    public RiskConfig getRiskConfig() { return riskConfig; }

    @Override
    public StrategyConfig getStrategyConfig() { return strategyConfig; }

    /**
     * Updates a single key in the .env file and reloads in-memory dotenv.
     * @deprecated Prefer injecting {@link EnvConfigPersistence} directly.
     */
    @Deprecated
    public void updateEnvProperty(String key, String value) {
        new EnvConfigPersistence().update(key, value);
        this.dotenv = Dotenv.configure().ignoreIfMissing().load();
    }
}
```

- [ ] **Step 3: Update EngineConfiguration to remove getInstance()**

In `src/main/java/com/rj/config/EngineConfiguration.java`, the `configManager()` bean method currently calls `ConfigManager.getInstance()`. Remove that method entirely — Spring will autowire the `@Component`-annotated `ConfigManager` automatically.

Delete this method from `EngineConfiguration`:

```java
// DELETE THIS:
@Bean
public ConfigManager configManager() {
    return ConfigManager.getInstance();
}
```

- [ ] **Step 4: Fix TradingEngine.create() — inject ConfigManager instead of getInstance()**

`TradingEngine.create()` calls `ConfigManager.getInstance()` on line 81. Since `TradingEngine` is not a Spring bean itself (it's built by `EngineConfiguration.tradingEngine()`), we need to pass `ConfigManager` in.

In `src/main/java/com/rj/config/EngineConfiguration.java`, update the `tradingEngine()` bean:

```java
@Bean
public TradingEngine tradingEngine(ConfigManager configManager) {
    return TradingEngine.create(configManager);
}
```

In `src/main/java/com/rj/engine/TradingEngine.java`, change `create()` to accept a `ConfigManager` parameter:

```java
public static TradingEngine create(ConfigManager config) {
    // Replace: ConfigManager config = ConfigManager.getInstance();
    // The rest of the method is unchanged
    RiskConfig riskCfg = config.getRiskConfig();
    // ... (rest of existing body unchanged)
}
```

- [ ] **Step 5: Run all tests and verify no compilation errors**

```bash
./mvnw test -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`. If any test references `ConfigManager.getInstance()`, inject it via `@MockBean` in the test instead.

- [ ] **Step 6: Verify no remaining getInstance() calls**

```bash
grep -rn "ConfigManager.getInstance()" src/
```

Expected: no output.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/rj/config/ConfigManager.java \
        src/main/java/com/rj/config/EngineConfiguration.java \
        src/main/java/com/rj/engine/TradingEngine.java
git commit -m "refactor(config): remove ConfigManager static singleton, convert to Spring @Component"
```

---

## Task 7: Extract EnvConfigPersistence

`ConfigManager.updateEnvProperty()` is a file write operation — a different concern from reading config. Callers of this method should inject `EnvConfigPersistence` instead of `ConfigManager`.

**Files:**
- Create: `src/main/java/com/rj/config/EnvConfigPersistence.java`

- [ ] **Step 1: Create EnvConfigPersistence**

Create `src/main/java/com/rj/config/EnvConfigPersistence.java`:

```java
package com.rj.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes key-value pairs to the .env file on disk.
 * Separated from ConfigManager (which is read-only after startup).
 */
@Component
public class EnvConfigPersistence {

    private static final Logger log = LoggerFactory.getLogger(EnvConfigPersistence.class);
    private static final Path ENV_PATH = Path.of(".env");

    /**
     * Updates {@code key} in .env to {@code value}, appending if not present.
     */
    public void update(String key, String value) {
        try {
            List<String> lines = Files.exists(ENV_PATH)
                    ? new ArrayList<>(Files.readAllLines(ENV_PATH))
                    : new ArrayList<>();

            String prefix = key + "=";
            boolean found = false;
            List<String> updated = lines.stream().map(line -> {
                if (line.startsWith(prefix)) return prefix + value;
                return line;
            }).collect(Collectors.toCollection(ArrayList::new));

            for (String line : updated) {
                if (line.startsWith(prefix)) { found = true; break; }
            }
            if (!found) updated.add(prefix + value);

            Files.write(ENV_PATH, updated);
            log.info("Updated {} in .env", key);
        } catch (IOException e) {
            log.error("Failed to update {} in .env: {}", key, e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Find callers of updateEnvProperty and switch them to EnvConfigPersistence**

```bash
grep -rn "updateEnvProperty" src/
```

For each caller found, add `EnvConfigPersistence` as a constructor-injected dependency and call `envConfigPersistence.update(key, value)` instead of `configManager.updateEnvProperty(key, value)`.

- [ ] **Step 3: Run all tests**

```bash
./mvnw test -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/rj/config/EnvConfigPersistence.java
git commit -m "refactor(config): extract EnvConfigPersistence — isolate .env write operations"
```

---

## Task 8: Fix broken StrategyControllerTest

The existing test does not declare `@MockBean` for `StrategyService`, so `when(strategyService.getAllStrategies())` operates on a null reference. Fix it.

**Files:**
- Modify: `src/test/java/com/rj/web/StrategyControllerTest.java`

- [ ] **Step 1: Fix the test**

Replace the full contents of `src/test/java/com/rj/web/StrategyControllerTest.java` with:

```java
package com.rj.web;

import com.rj.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StrategyController.class)
class StrategyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StrategyService strategyService;

    @Test
    void listStrategies_returnsAll() throws Exception {
        StrategyVersionInfo info = new StrategyVersionInfo(
                "test_strat", 1, 1, true, "ACTIVE", List.of(),
                new StrategyYamlConfig(), null);
        when(strategyService.getAllStrategies()).thenReturn(List.of(info));

        mockMvc.perform(get("/api/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].strategyId").value("test_strat"))
                .andExpect(jsonPath("$[0].activeVersion").value(1));
    }

    @Test
    void getStrategy_found_returnsStrategy() throws Exception {
        StrategyVersionInfo info = new StrategyVersionInfo(
                "test_strat", 1, 1, true, "ACTIVE", List.of(),
                new StrategyYamlConfig(), null);
        when(strategyService.getStrategy("test_strat")).thenReturn(info);

        mockMvc.perform(get("/api/strategies/test_strat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyId").value("test_strat"));
    }
}
```

- [ ] **Step 2: Run the test**

```bash
./mvnw test -pl . -Dtest=StrategyControllerTest -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, 2 tests passed.

- [ ] **Step 3: Run full test suite**

```bash
./mvnw test -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/rj/web/StrategyControllerTest.java
git commit -m "fix(test): add @MockBean to StrategyControllerTest"
```

---

## Self-Review

**Spec coverage:**
- EngineController split → Tasks 2, 3, 4, 5 ✓
- ConfigManager singleton removal → Task 6 ✓
- EnvConfigPersistence extraction → Task 7 ✓
- Missing Spring beans for DownloadTracker → Task 1 ✓
- Broken test fix → Task 8 ✓

**Placeholder scan:** No TBD, TODO, or "implement later" entries present.

**Type consistency:**
- `DownloadTracker`, `CandleDownloader`, `BrokerCircuitBreaker` introduced in Task 1 are used by name in Task 4 ✓
- `EnvConfigPersistence.update(key, value)` defined in Task 7, referenced in Task 6's `@Deprecated` wrapper ✓
- `TradingEngine.create(ConfigManager)` signature changed in Task 6 and called in `EngineConfiguration.tradingEngine(ConfigManager)` ✓
