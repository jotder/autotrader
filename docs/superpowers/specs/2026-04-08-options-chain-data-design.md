# Options Chain Data Layer — Design Spec (Subsystem A)

> **Scope:** This spec covers Subsystem A only — fetching, caching, serving, and archiving options chain data. Subsystems B (chain-aware strategy signals), C (single-leg options trading), and D (multi-leg options trading) are separate specs that depend on this one.

**Goal:** Provide a live, cached options chain for all F&O-eligible underlyings, refreshed via three paths (scheduled, event-driven, on-demand), exposed via REST, and archived daily to disk.

**Architecture:** `OptionChainService` holds a `ConcurrentHashMap` cache. Three refresh paths (scheduled 30s, event-triggered from `CandleAnalyzer`, REST on-demand) all run on virtual threads. `OptionChainController` exposes four endpoints. EOD archive written at market close by `EngineLifecycleManager`.

**Tech Stack:** Java 25, Spring Boot 3.4.4, Virtual Threads, `IMarketDataAdapter` (already wired to Fyers), Jackson, JUnit 5, Mockito, `@WebMvcTest`.

---

## Existing Infrastructure (No Changes Needed)

- `IMarketDataAdapter.getOptionChain(symbol, strikeCount, expiry)` — interface defined
- `FyersBrokerAdapter.getOptionChain()` — implemented, calls Fyers API
- `OptionChainResult` — full model with `OptionEntry` (OI, LTP, bid/ask, volume), `Expiry`, `VixData`
- `SymbolMasterCache` — used to check if an underlying is F&O-eligible
- `EngineLifecycleManager` — handles start/stop lifecycle hooks

---

## File Map

**Create:**
- `src/main/java/com/rj/engine/options/OptionChainSnapshot.java`
- `src/main/java/com/rj/engine/options/OptionChainService.java`
- `src/main/java/com/rj/config/OptionChainConfig.java`
- `src/main/java/com/rj/web/OptionChainController.java`
- `config/option-chain.yaml`
- `src/test/java/com/rj/engine/options/OptionChainServiceTest.java`
- `src/test/java/com/rj/web/OptionChainControllerTest.java`

**Modify:**
- `src/main/java/com/rj/engine/CandleAnalyzer.java` — inject `OptionChainService`; call `refresh()` on movement
- `src/main/java/com/rj/config/EngineConfiguration.java` — expose `OptionChainConfig` and `OptionChainService` as `@Bean`
- `src/main/java/com/rj/config/EngineLifecycleManager.java` — call `optionChainService.start()/stop()/archiveEod()`

---

## Section 1: Data Model

### `OptionChainSnapshot`

Immutable record wrapping `OptionChainResult` with fetch metadata and computed convenience values.

```java
package com.rj.engine.options;

import com.rj.model.OptionChainResult;
import java.time.Duration;
import java.time.Instant;

public record OptionChainSnapshot(
        String underlying,
        OptionChainResult data,
        Instant fetchedAt
) {
    public boolean isStale(Duration threshold) {
        return Duration.between(fetchedAt, Instant.now()).compareTo(threshold) > 0;
    }

    /** Put/Call ratio for a specific expiry date string (e.g. "2026-04-10"). */
    public double pcr(String expiry) {
        return data.expiries().stream()
                .filter(e -> e.date().equals(expiry))
                .findFirst()
                .map(e -> e.putOi() / Math.max(1.0, e.callOi()))
                .orElse(0.0);
    }

    /** Combined PCR across all expiries. */
    public double pcr() {
        long totalCallOi = data.expiries().stream().mapToLong(OptionChainResult.Expiry::callOi).sum();
        long totalPutOi  = data.expiries().stream().mapToLong(OptionChainResult.Expiry::putOi).sum();
        return totalPutOi / Math.max(1.0, totalCallOi);
    }
}
```

---

## Section 2: Configuration

### `config/option-chain.yaml`

```yaml
option-chain:
  enabled: true
  poll-interval-seconds: 30
  strike-count: 10              # strikes each side of ATM
  stale-threshold-seconds: 90  # REST returns stale flag if older than this
  refresh-rel-vol-threshold: 1.5  # trigger refresh when relVol exceeds this
  archive-path: data/option-chain
  underlyings:
    # Explicit list of underlyings to track.
    # If this list is non-empty, only these underlyings are tracked.
    # If empty ([]), OptionChainService loads all unique underlying symbols
    # from SymbolMasterCache (NSE_FO + BSE_FO + MCX_COM segments) at start().
    - NSE:NIFTY50-INDEX
    - NSE:NIFTY BANK
    # add more or leave empty to auto-discover from symbol master
```

### `OptionChainConfig`

```java
package com.rj.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "option-chain")
public class OptionChainConfig {
    private boolean enabled = true;
    private int pollIntervalSeconds = 30;
    private int strikeCount = 10;
    private int staleThresholdSeconds = 90;
    private double refreshRelVolThreshold = 1.5;
    private String archivePath = "data/option-chain";
    private List<String> underlyings = List.of();

    // standard getters and setters
}
```

---

## Section 3: Service

### `OptionChainService`

```java
package com.rj.engine.options;

import com.rj.broker.IMarketDataAdapter;
import com.rj.config.OptionChainConfig;
import com.rj.model.OptionChainResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public class OptionChainService {

    private static final Logger log = LoggerFactory.getLogger(OptionChainService.class);

    private final IMarketDataAdapter marketDataAdapter;
    private final OptionChainConfig config;
    private final SymbolMasterCache symbolMasterCache;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, OptionChainSnapshot> cache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private ScheduledExecutorService scheduler;
    private List<String> trackedUnderlyings; // resolved at start()

    public OptionChainService(IMarketDataAdapter marketDataAdapter, OptionChainConfig config,
                              SymbolMasterCache symbolMasterCache, ObjectMapper objectMapper) {
        this.marketDataAdapter = marketDataAdapter;
        this.config = config;
        this.symbolMasterCache = symbolMasterCache;
        this.objectMapper = objectMapper;
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    public void start() {
        if (!config.isEnabled()) {
            log.info("OptionChainService disabled — skipping start");
            return;
        }
        // Resolve tracked underlyings: explicit config list, or auto-discover from symbol master
        trackedUnderlyings = config.getUnderlyings().isEmpty()
                ? symbolMasterCache.allFnoUnderlyings()   // new method on SymbolMasterCache
                : config.getUnderlyings();
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("option-chain-scheduler").factory());
        scheduler.scheduleAtFixedRate(this::refreshAll,
                0, config.getPollIntervalSeconds(), TimeUnit.SECONDS);
        log.info("OptionChainService started — poll interval {}s, {} underlyings tracked",
                config.getPollIntervalSeconds(), trackedUnderlyings.size());
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        executor.shutdownNow();
        log.info("OptionChainService stopped");
    }

    // ── Refresh paths ───────────────────────────────────────────────────────

    /** Path 1: scheduled — refresh all configured underlyings. */
    private void refreshAll() {
        for (String underlying : config.getUnderlyings()) {
            try {
                fetchAndCache(underlying);
            } catch (Exception e) {
                log.warn("Scheduled refresh failed for {}: {}", underlying, e.getMessage());
            }
        }
    }

    /**
     * Path 2: event-triggered — called by CandleAnalyzer on movement detection.
     * Debounced: no-op if a fetch is already in-flight for this underlying.
     */
    public void refresh(String underlying) {
        if (!config.isEnabled()) return;
        if (!config.getUnderlyings().contains(underlying)) return;
        if (!inFlight.add(underlying)) return; // already in-flight
        executor.submit(() -> {
            try {
                fetchAndCache(underlying);
            } catch (Exception e) {
                log.warn("Event-triggered refresh failed for {}: {}", underlying, e.getMessage());
            } finally {
                inFlight.remove(underlying);
            }
        });
    }

    /**
     * Path 3: on-demand — called by REST endpoint.
     * Returns cached snapshot if present; triggers synchronous fetch on cold start.
     */
    public Optional<OptionChainSnapshot> getChain(String underlying) {
        OptionChainSnapshot cached = cache.get(underlying);
        if (cached != null) return Optional.of(cached);
        // Cold start: synchronous fetch
        try {
            fetchAndCache(underlying);
            return Optional.ofNullable(cache.get(underlying));
        } catch (Exception e) {
            log.error("On-demand fetch failed for {}: {}", underlying, e.getMessage());
            return Optional.empty();
        }
    }

    public Map<String, OptionChainSnapshot> allSnapshots() {
        return Collections.unmodifiableMap(cache);
    }

    // ── EOD archive ─────────────────────────────────────────────────────────

    public void archiveEod() {
        if (cache.isEmpty()) return;
        String date = LocalDate.now().toString();
        Path dir = Path.of(config.getArchivePath(), date);
        try {
            Files.createDirectories(dir);
            for (var entry : cache.entrySet()) {
                String filename = entry.getKey().replace(":", "_").replace(" ", "_") + ".json";
                Path file = dir.resolve(filename);
                // Serialize via Jackson (wired via Spring ObjectMapper)
                // Implementation note: serialize via Jackson ObjectMapper injected into service.
            // OptionChainResult must be Jackson-serializable (verify @JsonProperty annotations).
            Files.writeString(file, objectMapper.writeValueAsString(entry.getValue().data()));
                log.info("Archived option chain for {} to {}", entry.getKey(), file);
            }
        } catch (IOException e) {
            log.error("EOD archive failed: {}", e.getMessage(), e);
        }
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private void fetchAndCache(String underlying) {
        // Fetch all active expiries (expiry = "" signals "all" to Fyers API)
        OptionChainResult result = marketDataAdapter.getOptionChain(
                underlying, config.getStrikeCount(), "");
        if (result == null) {
            log.warn("getOptionChain returned null for {}", underlying);
            return;
        }
        cache.put(underlying, new OptionChainSnapshot(underlying, result, Instant.now()));
        log.debug("Cached option chain for {} — {} expiries", underlying,
                result.expiries() != null ? result.expiries().size() : 0);
    }
}
```

---

## Section 4: REST API

### `OptionChainController`

```java
package com.rj.web;

import com.rj.config.OptionChainConfig;
import com.rj.engine.options.OptionChainService;
import com.rj.engine.options.OptionChainSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/option-chain")
public class OptionChainController {

    private final OptionChainService optionChainService;
    private final OptionChainConfig config;

    public OptionChainController(OptionChainService optionChainService, OptionChainConfig config) {
        this.optionChainService = optionChainService;
        this.config = config;
    }

    /** Full chain for one underlying — all expiries. */
    @GetMapping("/{underlying}")
    public ResponseEntity<?> getChain(@PathVariable String underlying,
                                      @RequestParam(required = false) String expiry) {
        return optionChainService.getChain(underlying)
                .map(snap -> ResponseEntity.ok(toResponse(snap, expiry)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Manual refresh trigger — returns 202 Accepted immediately. */
    @PostMapping("/{underlying}/refresh")
    public ResponseEntity<?> refresh(@PathVariable String underlying) {
        optionChainService.refresh(underlying);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Refresh triggered for " + underlying,
                "underlying", underlying));
    }

    /** Summary row per underlying: ATM, PCR, VIX, stale flag. */
    @GetMapping("/summary")
    public List<Map<String, Object>> summary() {
        Duration staleThreshold = Duration.ofSeconds(config.getStaleThresholdSeconds());
        return optionChainService.allSnapshots().entrySet().stream()
                .map(e -> {
                    OptionChainSnapshot snap = e.getValue();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("underlying", snap.underlying());
                    row.put("pcr", snap.pcr());
                    row.put("vix", snap.data().vix() != null ? snap.data().vix().value() : null);
                    row.put("fetchedAt", snap.fetchedAt());
                    row.put("stale", snap.isStale(staleThreshold));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> toResponse(OptionChainSnapshot snap, String expiryFilter) {
        Duration staleThreshold = Duration.ofSeconds(config.getStaleThresholdSeconds());
        var expiries = snap.data().expiries();
        if (expiryFilter != null && !expiryFilter.isBlank()) {
            expiries = expiries == null ? null : expiries.stream()
                    .filter(e -> e.date().equals(expiryFilter))
                    .toList();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("underlying", snap.underlying());
        result.put("fetchedAt", snap.fetchedAt());
        result.put("stale", snap.isStale(staleThreshold));
        result.put("vix", snap.data().vix() != null ? snap.data().vix().value() : null);
        result.put("expiries", expiries);
        return result;
    }
}
```

---

## Section 5: Movement Trigger in CandleAnalyzer

`CandleAnalyzer` gets `OptionChainService` via setter injection (optional — null-safe). The threshold logic lives inside the service so `CandleAnalyzer` has no config dependency.

`OptionChainService` gains an overloaded `refresh` that encapsulates the threshold check:

```java
/**
 * Called by CandleAnalyzer after each candle close.
 * Triggers async refresh only when the move is significant.
 */
public void refreshIfSignificant(String underlying, MarketState state, double relVol) {
    boolean strongMove = state == MarketState.STRONG_BULLISH || state == MarketState.STRONG_BEARISH;
    boolean highVolume  = relVol > config.getRefreshRelVolThreshold();
    if (strongMove || highVolume) {
        refresh(underlying);
    }
}
```

In `CandleAnalyzer`, after `MarketState` computation:

```java
// Inject via setter (set in EngineConfiguration after construction)
private OptionChainService optionChainService;
public void setOptionChainService(OptionChainService svc) { this.optionChainService = svc; }

// After MarketState is computed:
if (optionChainService != null) {
    optionChainService.refreshIfSignificant(underlying, state, relVol);
}
```

---

## Section 6: EngineConfiguration Wiring

Add to `EngineConfiguration`:

```java
@Bean
public OptionChainConfig optionChainConfig() {
    return new OptionChainConfig(); // populated by @ConfigurationProperties
}

@Bean
public OptionChainService optionChainService(IMarketDataAdapter marketDataAdapter,
                                              OptionChainConfig optionChainConfig,
                                              SymbolMasterCache symbolMasterCache,
                                              ObjectMapper objectMapper) {
    return new OptionChainService(marketDataAdapter, optionChainConfig,
                                  symbolMasterCache, objectMapper);
}
```

In `tradingEngine()` factory, after construction:
```java
candleAnalyzer.setOptionChainService(optionChainService);
```

In `EngineLifecycleManager`:
```java
// onStart():  optionChainService.start();
// onStop():   optionChainService.archiveEod(); optionChainService.stop();
```

---

## Section 7: Testing

### `OptionChainServiceTest`

- Mock `IMarketDataAdapter` returning a fixed `OptionChainResult`
- **Cache population**: `getChain()` on cold start triggers fetch and caches result
- **Debounce**: calling `refresh()` twice rapidly — `getOptionChain()` called only once
- **Scheduled refresh**: advance scheduler, verify cache updated
- **Failure resilience**: `getOptionChain()` throws → `Optional.empty()` returned, no crash
- **PCR computation**: `snapshot.pcr()` returns correct put/call ratio

### `OptionChainControllerTest` (`@WebMvcTest`)

- `GET /api/option-chain/{underlying}` → 200 with snapshot JSON
- `GET /api/option-chain/{underlying}?expiry=2026-04-10` → filtered expiry only
- `GET /api/option-chain/unknown` → 404 when service returns `Optional.empty()`
- `POST /api/option-chain/{underlying}/refresh` → 202 Accepted
- `GET /api/option-chain/summary` → list with pcr, vix, stale flag per underlying

---

## REST Endpoint Summary

| Method | URL | Description |
|---|---|---|
| `GET` | `/api/option-chain/{underlying}` | Full chain — all expiries |
| `GET` | `/api/option-chain/{underlying}?expiry=YYYY-MM-DD` | Single expiry |
| `POST` | `/api/option-chain/{underlying}/refresh` | Manual trigger (202 Accepted) |
| `GET` | `/api/option-chain/summary` | PCR + ATM + VIX per underlying |

---

## Data Flow

```
Market hours:
  ScheduledExecutorService (30s)  ──→ fetchAndCache(underlying)  ──→ cache
  CandleAnalyzer (on movement)    ──→ refresh(underlying)        ──→ executor → fetchAndCache
  REST GET /api/option-chain/...  ──→ getChain() → cache hit / cold fetch

EOD (15:30 IST):
  EngineLifecycleManager.onMarketClose() → archiveEod()
    → data/option-chain/YYYY-MM-DD/{underlying}.json
```

---

*Spec written: 2026-04-08 · Subsystem A of 4 (B=signals, C=single-leg, D=multi-leg)*
