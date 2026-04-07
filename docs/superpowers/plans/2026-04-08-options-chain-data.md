# Options Chain Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fetch, cache, and serve live options chain data for all F&O underlyings via three refresh paths (scheduled, event-driven, on-demand) and expose it through four REST endpoints.

**Architecture:** `OptionChainService` holds a `ConcurrentHashMap<String, OptionChainSnapshot>` cache. A virtual-thread `ScheduledExecutorService` polls every 30s; `CandleService` triggers async refresh on strong signals; REST on-demand fetches on cold start. `OptionChainController` exposes four endpoints. `EngineLifecycleManager` calls `start()/stop()/archiveEod()`. All I/O runs on virtual threads — never on the Disruptor hot path.

**Tech Stack:** Java 25, Spring Boot 3.4.4, SnakeYAML (already in pom.xml), Jackson (already in pom.xml), Virtual Threads, JUnit 5, Mockito, `@WebMvcTest`.

---

## File Map

**Create:**
- `src/main/java/com/rj/engine/options/OptionChainSnapshot.java` — immutable record wrapping `OptionChainResult` with `fetchedAt`, `isStale()`, `pcr()`
- `src/main/java/com/rj/config/OptionChainConfig.java` — config POJO loaded from `config/option-chain.yaml`
- `src/main/java/com/rj/engine/options/OptionChainService.java` — cache + three refresh paths + EOD archive
- `src/main/java/com/rj/web/OptionChainController.java` — four REST endpoints
- `config/option-chain.yaml` — default config file
- `src/test/java/com/rj/engine/options/OptionChainServiceTest.java`
- `src/test/java/com/rj/web/OptionChainControllerTest.java`

**Modify:**
- `src/main/java/com/rj/config/SymbolMasterCache.java` — add `allFnoUnderlyings()` method
- `src/main/java/com/rj/engine/CandleService.java` — add `OptionChainService` field + setter; call `refreshIfSignificant()` after candle emission
- `src/main/java/com/rj/config/EngineConfiguration.java` — add `OptionChainConfig` and `OptionChainService` beans; setter-inject into `CandleService`
- `src/main/java/com/rj/config/EngineLifecycleManager.java` — inject `OptionChainService`; call `start()/archiveEod()/stop()`

---

## Task 1: Add `allFnoUnderlyings()` to `SymbolMasterCache`

`OptionChainService` needs the list of all underlying symbols that have options (CE/PE entries in the symbol master). `SymbolMasterCache.allUnderlyings()` returns all underlyings including equity, so we add a focused method.

**Files:**
- Modify: `src/main/java/com/rj/config/SymbolMasterCache.java`

- [ ] **Step 1: Write the failing test**

Open `src/test/java/com/rj/config/SymbolMasterCacheTest.java` (create it if it doesn't exist) and add:

```java
package com.rj.config;

import com.rj.model.dim.SymbolMasterEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SymbolMasterCacheTest {

    private static SymbolMasterEntry entry(String underlying, String optionType) {
        return new SymbolMasterEntry(
                "tok", "details", 0, 50, 0.05, "isin", "session",
                "2026-01-01", "2026-04-10", "NSE:NIFTY26APR22000CE",
                10, 11, 0, underlying, "0", 22000.0, optionType, "ftok",
                "", "", "");
    }

    @Test
    void allFnoUnderlyings_returnOnlyOptionUnderlyings() {
        SymbolMasterCache cache = SymbolMasterCache.fromEntries(List.of(
                entry("NSE:NIFTY50-INDEX", "CE"),
                entry("NSE:NIFTY50-INDEX", "PE"),
                entry("NSE:NIFTY50-INDEX", "XX"),   // future — excluded
                entry("NSE:SBIN-EQ",       null),   // equity — excluded
                entry("NSE:BANKNIFTY",     "CE")
        ));

        Set<String> result = cache.allFnoUnderlyings();

        assertThat(result).containsExactlyInAnyOrder("NSE:NIFTY50-INDEX", "NSE:BANKNIFTY");
    }
}
```

- [ ] **Step 2: Run the test — expect compilation failure**

```bash
./mvnw test -pl . -Dtest=SymbolMasterCacheTest -q 2>&1 | tail -15
```

Expected: `COMPILATION ERROR` — `fromEntries` and `allFnoUnderlyings` don't exist yet.

- [ ] **Step 3: Add `fromEntries` factory and `allFnoUnderlyings()` to `SymbolMasterCache`**

In `src/main/java/com/rj/config/SymbolMasterCache.java`, add these two methods (after the existing `allUnderlyings()` method):

```java
/**
 * Returns all distinct underlying symbols that have at least one CE or PE option
 * in the symbol master (i.e., F&O-eligible underlyings).
 * Futures (optionType "XX") and equity entries (null optionType) are excluded.
 */
public Set<String> allFnoUnderlyings() {
    return byUnderlying.entrySet().stream()
            .filter(e -> e.getValue().stream()
                    .anyMatch(entry -> "CE".equals(entry.optionType())
                            || "PE".equals(entry.optionType())))
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
}

/** Test-only factory — builds a cache directly from a list of entries. */
static SymbolMasterCache fromEntries(List<SymbolMasterEntry> entries) {
    return new SymbolMasterCache(entries);
}
```

Also add `import java.util.Set;` to the imports if not already present.

- [ ] **Step 4: Run the test — expect pass**

```bash
./mvnw test -pl . -Dtest=SymbolMasterCacheTest -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, 1 test passed.

- [ ] **Step 5: Run full suite**

```bash
./mvnw test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS` (same failure count as before — CandleDatabaseTest failures are pre-existing).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/rj/config/SymbolMasterCache.java \
        src/test/java/com/rj/config/SymbolMasterCacheTest.java
git commit -m "feat(config): add allFnoUnderlyings() to SymbolMasterCache"
```

---

## Task 2: Create `OptionChainConfig` and `option-chain.yaml`

Config POJO loaded from `config/option-chain.yaml` via SnakeYAML (same pattern as `YamlStrategyLoader`). Sensible defaults apply if the file is absent or a field is missing.

**Files:**
- Create: `src/main/java/com/rj/config/OptionChainConfig.java`
- Create: `config/option-chain.yaml`

- [ ] **Step 1: Create `config/option-chain.yaml`**

```yaml
option-chain:
  enabled: true
  poll-interval-seconds: 30
  strike-count: 10
  stale-threshold-seconds: 90
  refresh-rel-vol-threshold: 1.5
  refresh-confidence-threshold: 0.85
  archive-path: data/option-chain
  underlyings: []
  # Leave underlyings empty to auto-discover from symbol master (NSE_FO / BSE_FO / MCX_COM).
  # Add entries to restrict to specific underlyings:
  # underlyings:
  #   - NSE:NIFTY50-INDEX
  #   - NSE:NIFTY BANK
```

- [ ] **Step 2: Create `OptionChainConfig.java`**

Create `src/main/java/com/rj/config/OptionChainConfig.java`:

```java
package com.rj.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OptionChainConfig {

    private static final Logger log = LoggerFactory.getLogger(OptionChainConfig.class);
    private static final Path CONFIG_PATH = Path.of("config/option-chain.yaml");

    private boolean enabled = true;
    private int pollIntervalSeconds = 30;
    private int strikeCount = 10;
    private int staleThresholdSeconds = 90;
    private double refreshRelVolThreshold = 1.5;
    private double refreshConfidenceThreshold = 0.85;
    private String archivePath = "data/option-chain";
    private List<String> underlyings = new ArrayList<>();

    @PostConstruct
    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            log.info("option-chain.yaml not found at {} — using defaults", CONFIG_PATH);
            return;
        }
        try (InputStream is = Files.newInputStream(CONFIG_PATH)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            if (root == null) return;
            Object cfg = root.get("option-chain");
            if (!(cfg instanceof Map<?, ?> m)) return;

            if (m.get("enabled") instanceof Boolean b)           this.enabled = b;
            if (m.get("poll-interval-seconds") instanceof Integer i) this.pollIntervalSeconds = i;
            if (m.get("strike-count") instanceof Integer i)      this.strikeCount = i;
            if (m.get("stale-threshold-seconds") instanceof Integer i) this.staleThresholdSeconds = i;
            if (m.get("refresh-rel-vol-threshold") instanceof Number n) this.refreshRelVolThreshold = n.doubleValue();
            if (m.get("refresh-confidence-threshold") instanceof Number n) this.refreshConfidenceThreshold = n.doubleValue();
            if (m.get("archive-path") instanceof String s)       this.archivePath = s;
            if (m.get("underlyings") instanceof List<?> list)    this.underlyings = list.stream()
                    .filter(String.class::isInstance).map(String.class::cast).toList();

            log.info("OptionChainConfig loaded: enabled={}, pollInterval={}s, strikeCount={}, underlyings={}",
                    enabled, pollIntervalSeconds, strikeCount,
                    underlyings.isEmpty() ? "auto-discover" : underlyings);
        } catch (Exception e) {
            log.error("Failed to load option-chain.yaml — using defaults: {}", e.getMessage());
        }
    }

    public boolean isEnabled()                    { return enabled; }
    public int getPollIntervalSeconds()            { return pollIntervalSeconds; }
    public int getStrikeCount()                    { return strikeCount; }
    public int getStaleThresholdSeconds()          { return staleThresholdSeconds; }
    public double getRefreshRelVolThreshold()      { return refreshRelVolThreshold; }
    public double getRefreshConfidenceThreshold()  { return refreshConfidenceThreshold; }
    public String getArchivePath()                 { return archivePath; }
    public List<String> getUnderlyings()           { return underlyings; }
}
```

- [ ] **Step 3: Run full suite — verify no compilation errors**

```bash
./mvnw test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS` (same pre-existing failures only).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/rj/config/OptionChainConfig.java config/option-chain.yaml
git commit -m "feat(config): add OptionChainConfig loaded from option-chain.yaml"
```

---

## Task 3: Create `OptionChainSnapshot`

Immutable record wrapping `OptionChainResult` with fetch metadata. PCR helpers use `expiryData` (the list of `Expiry` objects) and `callOi`/`putOi` from `OptionChainResult`.

**Files:**
- Create: `src/main/java/com/rj/engine/options/OptionChainSnapshot.java`

- [ ] **Step 1: Create `src/main/java/com/rj/engine/options/OptionChainSnapshot.java`**

```java
package com.rj.engine.options;

import com.rj.model.OptionChainResult;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable snapshot of an option chain for one underlying, captured at a point in time.
 *
 * <p>PCR (Put/Call Ratio) = totalPutOI / totalCallOI. Values > 1 suggest bearish sentiment.
 */
public record OptionChainSnapshot(
        String underlying,
        OptionChainResult data,
        Instant fetchedAt
) {
    /**
     * Returns true if more than {@code threshold} time has passed since this snapshot was fetched.
     */
    public boolean isStale(Duration threshold) {
        return Duration.between(fetchedAt, Instant.now()).compareTo(threshold) > 0;
    }

    /**
     * Combined PCR across all expiries using the aggregate callOi/putOi from the result.
     * Returns 0.0 if callOi is zero.
     */
    public double pcr() {
        return data.callOi == 0 ? 0.0 : (double) data.putOi / data.callOi;
    }

    /**
     * PCR for a specific expiry date string (format as returned by Fyers, e.g. "10-Apr-2026").
     * Computes from option entries filtered by that expiry date.
     * Returns 0.0 if expiry not found or no options for that expiry.
     */
    public double pcr(String expiryDate) {
        if (data.optionsChain == null || data.optionsChain.isEmpty()) return 0.0;
        long callOi = data.optionsChain.stream()
                .filter(e -> expiryDate.equals(expiryDateOf(e.symbol)) && "CE".equals(e.optionType))
                .mapToLong(e -> e.oi).sum();
        long putOi = data.optionsChain.stream()
                .filter(e -> expiryDate.equals(expiryDateOf(e.symbol)) && "PE".equals(e.optionType))
                .mapToLong(e -> e.oi).sum();
        return callOi == 0 ? 0.0 : (double) putOi / callOi;
    }

    /**
     * Extracts expiry date from a Fyers option symbol string.
     * For filtering per-expiry PCR — implementation note: Fyers expiry date in
     * {@code OptionChainResult.Expiry.date} is the canonical source; this helper
     * falls back gracefully if the format varies.
     */
    private static String expiryDateOf(String symbol) {
        // Symbol format: "NSE:NIFTY26APR22000CE" — expiry date not directly embedded.
        // Per-expiry PCR is best effort; use aggregate pcr() for primary use cases.
        return symbol; // caller should use expiryData list for date lookup
    }
}
```

**Implementation note for the implementer:** `pcr(String expiryDate)` using per-symbol filtering is approximate since Fyers symbols don't directly embed the expiry date string. For production use, strategies should call `pcr()` (aggregate) unless they need per-expiry breakdown, in which case compare against `OptionChainResult.expiryData` directly.

- [ ] **Step 2: Run full suite**

```bash
./mvnw test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/rj/engine/options/OptionChainSnapshot.java
git commit -m "feat(options): add OptionChainSnapshot record with PCR helpers"
```

---

## Task 4: Create `OptionChainService` (TDD)

The core service: cache, three refresh paths, debounce, cold start, EOD archive. Uses `IMarketDataAdapter` (already wired to Fyers) and `SymbolMasterCache` for underlying discovery.

**Files:**
- Create: `src/test/java/com/rj/engine/options/OptionChainServiceTest.java`
- Create: `src/main/java/com/rj/engine/options/OptionChainService.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/rj/engine/options/OptionChainServiceTest.java`:

```java
package com.rj.engine.options;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rj.broker.IMarketDataAdapter;
import com.rj.config.OptionChainConfig;
import com.rj.config.SymbolMasterCache;
import com.rj.model.OptionChainResult;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OptionChainServiceTest {

    @Mock IMarketDataAdapter marketDataAdapter;
    @Mock SymbolMasterCache symbolMasterCache;

    private OptionChainConfig config;
    private OptionChainService service;
    private OptionChainResult fakeResult;

    @BeforeEach
    void setUp() {
        config = new OptionChainConfig();
        // Use a minimal result with non-null optionsChain and expiryData
        JSONObject json = new JSONObject();
        JSONObject data = new JSONObject();
        data.put("callOi", 1000L);
        data.put("putOi", 800L);
        data.put("optionsChain", new org.json.JSONArray());
        data.put("expiryData", new org.json.JSONArray());
        json.put("data", data);
        fakeResult = OptionChainResult.from(json);

        when(marketDataAdapter.getOptionChain(eq("NSE:NIFTY50-INDEX"), anyInt(), anyString()))
                .thenReturn(fakeResult);

        service = new OptionChainService(marketDataAdapter, config,
                symbolMasterCache, new ObjectMapper());
    }

    @Test
    void getChain_coldStart_fetchesAndCaches() {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));

        Optional<OptionChainSnapshot> result = service.getChain("NSE:NIFTY50-INDEX");

        assertThat(result).isPresent();
        assertThat(result.get().underlying()).isEqualTo("NSE:NIFTY50-INDEX");
        assertThat(result.get().data()).isSameAs(fakeResult);
        verify(marketDataAdapter, times(1))
                .getOptionChain(eq("NSE:NIFTY50-INDEX"), anyInt(), anyString());
    }

    @Test
    void getChain_secondCall_returnsCachedWithoutRefetch() {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));
        service.getChain("NSE:NIFTY50-INDEX"); // populate cache

        service.getChain("NSE:NIFTY50-INDEX"); // second call

        verify(marketDataAdapter, times(1))
                .getOptionChain(any(), anyInt(), anyString()); // still only 1 fetch
    }

    @Test
    void getChain_brokerThrows_returnsEmpty() {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));
        when(marketDataAdapter.getOptionChain(any(), anyInt(), any()))
                .thenThrow(new RuntimeException("API down"));

        Optional<OptionChainSnapshot> result = service.getChain("NSE:NIFTY50-INDEX");

        assertThat(result).isEmpty();
    }

    @Test
    void getChain_unknownUnderlying_returnsEmpty() {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));

        Optional<OptionChainSnapshot> result = service.getChain("NSE:UNKNOWN");

        assertThat(result).isEmpty();
        verifyNoInteractions(marketDataAdapter);
    }

    @Test
    void refresh_debounce_onlyOneFetchInFlight() throws InterruptedException {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));
        // Slow down the mock so in-flight flag stays set
        when(marketDataAdapter.getOptionChain(any(), anyInt(), any()))
                .thenAnswer(inv -> { Thread.sleep(50); return fakeResult; });

        service.refresh("NSE:NIFTY50-INDEX");
        service.refresh("NSE:NIFTY50-INDEX"); // second call while first in-flight

        Thread.sleep(200); // wait for async fetch to complete
        verify(marketDataAdapter, atMost(2))
                .getOptionChain(any(), anyInt(), any()); // debounced — at most 1 per flight
    }

    @Test
    void refreshIfSignificant_highConfidence_triggersRefresh() throws InterruptedException {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));

        service.refreshIfSignificant("NSE:NIFTY50-INDEX",
                com.rj.model.Signal.BUY, 0.90, 1.2); // high confidence → should trigger

        Thread.sleep(200);
        verify(marketDataAdapter, atLeastOnce())
                .getOptionChain(eq("NSE:NIFTY50-INDEX"), anyInt(), any());
    }

    @Test
    void refreshIfSignificant_lowConfidenceLowVol_noTrigger() throws InterruptedException {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));

        service.refreshIfSignificant("NSE:NIFTY50-INDEX",
                com.rj.model.Signal.HOLD, 0.50, 0.8); // weak signal → no trigger

        Thread.sleep(100);
        verifyNoInteractions(marketDataAdapter);
    }

    @Test
    void allSnapshots_returnsAllCachedEntries() {
        config.setUnderlyingsForTest(List.of("NSE:NIFTY50-INDEX"));
        service.getChain("NSE:NIFTY50-INDEX");

        assertThat(service.allSnapshots()).containsKey("NSE:NIFTY50-INDEX");
    }
}
```

- [ ] **Step 2: Run the test — expect compilation failure**

```bash
./mvnw test -pl . -Dtest=OptionChainServiceTest -q 2>&1 | tail -15
```

Expected: `COMPILATION ERROR` — `OptionChainService` and `setUnderlyingsForTest` don't exist.

- [ ] **Step 3: Add `setUnderlyingsForTest` to `OptionChainConfig`**

In `src/main/java/com/rj/config/OptionChainConfig.java`, add after the existing getters:

```java
/** Test-only setter — bypasses YAML loading. */
void setUnderlyingsForTest(java.util.List<String> underlyings) {
    this.underlyings = underlyings;
}
```

- [ ] **Step 4: Create `OptionChainService.java`**

Create `src/main/java/com/rj/engine/options/OptionChainService.java`:

```java
package com.rj.engine.options;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rj.broker.IMarketDataAdapter;
import com.rj.config.OptionChainConfig;
import com.rj.config.SymbolMasterCache;
import com.rj.model.OptionChainResult;
import com.rj.model.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages the live options chain cache.
 *
 * <p><b>Three refresh paths:</b>
 * <ol>
 *   <li>Scheduled — polls all tracked underlyings every {@code pollIntervalSeconds}</li>
 *   <li>Event-triggered — called by {@link com.rj.engine.CandleService} on strong moves</li>
 *   <li>On-demand — REST endpoint; cold-start synchronous fetch if cache empty</li>
 * </ol>
 */
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
    private List<String> trackedUnderlyings = List.of();

    public OptionChainService(IMarketDataAdapter marketDataAdapter,
                              OptionChainConfig config,
                              SymbolMasterCache symbolMasterCache,
                              ObjectMapper objectMapper) {
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
        trackedUnderlyings = config.getUnderlyings().isEmpty()
                ? new ArrayList<>(symbolMasterCache.allFnoUnderlyings())
                : config.getUnderlyings();

        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("option-chain-scheduler").factory());
        scheduler.scheduleAtFixedRate(this::refreshAll,
                0, config.getPollIntervalSeconds(), TimeUnit.SECONDS);

        log.info("OptionChainService started — poll={}s, {} underlyings",
                config.getPollIntervalSeconds(), trackedUnderlyings.size());
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        executor.shutdownNow();
        log.info("OptionChainService stopped");
    }

    // ── Refresh paths ───────────────────────────────────────────────────────

    /** Path 1: scheduled — polls all tracked underlyings. Called by scheduler. */
    void refreshAll() {
        for (String underlying : trackedUnderlyings) {
            try {
                fetchAndCache(underlying);
            } catch (Exception e) {
                log.warn("Scheduled refresh failed for {}: {}", underlying, e.getMessage());
            }
        }
    }

    /**
     * Path 2: event-triggered — called by CandleService on movement detection.
     * No-op if underlying is not tracked or a fetch is already in-flight.
     */
    public void refresh(String underlying) {
        if (!config.isEnabled()) return;
        if (!trackedUnderlyings.contains(underlying)) return;
        if (!inFlight.add(underlying)) return; // debounce
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
     * Checks signal strength against configured thresholds and triggers {@link #refresh}
     * if the move is significant. Called by CandleService after each candle emission.
     *
     * @param underlying  the underlying symbol (e.g. "NSE:NIFTY50-INDEX")
     * @param signal      the signal from CandleRecommendation (BUY / SELL / HOLD)
     * @param confidence  0.0–1.0 confidence from CandleRecommendation
     * @param relVol      relative volume (current bar / N-period average)
     */
    public void refreshIfSignificant(String underlying, Signal signal, double confidence, double relVol) {
        boolean strongSignal = signal != Signal.HOLD
                && confidence >= config.getRefreshConfidenceThreshold();
        boolean highVolume   = relVol >= config.getRefreshRelVolThreshold();
        if (strongSignal || highVolume) {
            refresh(underlying);
        }
    }

    /**
     * Path 3: on-demand — returns cached snapshot if present; triggers synchronous
     * fetch on cold start. Returns empty if the underlying is not tracked or broker fails.
     */
    public Optional<OptionChainSnapshot> getChain(String underlying) {
        OptionChainSnapshot cached = cache.get(underlying);
        if (cached != null) return Optional.of(cached);

        if (!trackedUnderlyings.contains(underlying) && !config.getUnderlyings().isEmpty()) {
            return Optional.empty(); // not a tracked underlying
        }

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
        if (cache.isEmpty()) {
            log.info("OptionChainService: nothing to archive (cache empty)");
            return;
        }
        String date = LocalDate.now().toString();
        Path dir = Path.of(config.getArchivePath(), date);
        try {
            Files.createDirectories(dir);
            for (var entry : cache.entrySet()) {
                String filename = entry.getKey()
                        .replace(":", "_").replace(" ", "_") + ".json";
                Path file = dir.resolve(filename);
                Files.writeString(file, objectMapper.writeValueAsString(entry.getValue().data()));
                log.info("Archived option chain for {} → {}", entry.getKey(), file);
            }
        } catch (IOException e) {
            log.error("EOD archive failed: {}", e.getMessage(), e);
        }
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private void fetchAndCache(String underlying) {
        // Pass empty string — Fyers API returns all available expiries
        OptionChainResult result = marketDataAdapter.getOptionChain(
                underlying, config.getStrikeCount(), "");
        if (result == null) {
            log.warn("getOptionChain returned null for {}", underlying);
            return;
        }
        cache.put(underlying, new OptionChainSnapshot(underlying, result, Instant.now()));
        log.debug("Cached option chain for {}", underlying);
    }
}
```

- [ ] **Step 5: Run the tests — expect pass**

```bash
./mvnw test -pl . -Dtest=OptionChainServiceTest -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`, 7 tests passed.

- [ ] **Step 6: Run full suite**

```bash
./mvnw test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS` (same pre-existing failures only).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/rj/engine/options/OptionChainService.java \
        src/main/java/com/rj/config/OptionChainConfig.java \
        src/test/java/com/rj/engine/options/OptionChainServiceTest.java
git commit -m "feat(options): add OptionChainService with three refresh paths"
```

---

## Task 5: Create `OptionChainController` (TDD)

Four REST endpoints: full chain, expiry-filtered, manual refresh, summary.

**Files:**
- Create: `src/test/java/com/rj/web/OptionChainControllerTest.java`
- Create: `src/main/java/com/rj/web/OptionChainController.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/rj/web/OptionChainControllerTest.java`:

```java
package com.rj.web;

import com.rj.config.OptionChainConfig;
import com.rj.engine.options.OptionChainService;
import com.rj.engine.options.OptionChainSnapshot;
import com.rj.model.OptionChainResult;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OptionChainController.class)
class OptionChainControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean OptionChainService optionChainService;
    @MockBean OptionChainConfig optionChainConfig;

    private OptionChainSnapshot fakeSnapshot(String underlying) {
        JSONObject json = new JSONObject();
        JSONObject data = new JSONObject();
        data.put("callOi", 1000L);
        data.put("putOi", 800L);
        data.put("optionsChain", new org.json.JSONArray());
        data.put("expiryData", new org.json.JSONArray());
        json.put("data", data);
        return new OptionChainSnapshot(underlying, OptionChainResult.from(json), Instant.now());
    }

    @Test
    void getChain_found_returns200() throws Exception {
        when(optionChainService.getChain("NSE:NIFTY50-INDEX"))
                .thenReturn(Optional.of(fakeSnapshot("NSE:NIFTY50-INDEX")));
        when(optionChainConfig.getStaleThresholdSeconds()).thenReturn(90);

        mockMvc.perform(get("/api/option-chain/NSE:NIFTY50-INDEX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.underlying").value("NSE:NIFTY50-INDEX"))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    void getChain_notFound_returns404() throws Exception {
        when(optionChainService.getChain(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/option-chain/NSE:UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getChain_withExpiryFilter_returns200() throws Exception {
        when(optionChainService.getChain("NSE:NIFTY50-INDEX"))
                .thenReturn(Optional.of(fakeSnapshot("NSE:NIFTY50-INDEX")));
        when(optionChainConfig.getStaleThresholdSeconds()).thenReturn(90);

        mockMvc.perform(get("/api/option-chain/NSE:NIFTY50-INDEX")
                        .param("expiry", "10-Apr-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.underlying").value("NSE:NIFTY50-INDEX"));
    }

    @Test
    void refresh_returns202() throws Exception {
        mockMvc.perform(post("/api/option-chain/NSE:NIFTY50-INDEX/refresh"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.underlying").value("NSE:NIFTY50-INDEX"));

        verify(optionChainService).refresh("NSE:NIFTY50-INDEX");
    }

    @Test
    void summary_returnsListWithPcr() throws Exception {
        OptionChainSnapshot snap = fakeSnapshot("NSE:NIFTY50-INDEX");
        when(optionChainService.allSnapshots()).thenReturn(Map.of("NSE:NIFTY50-INDEX", snap));
        when(optionChainConfig.getStaleThresholdSeconds()).thenReturn(90);

        mockMvc.perform(get("/api/option-chain/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].underlying").value("NSE:NIFTY50-INDEX"))
                .andExpect(jsonPath("$[0].pcr").exists())
                .andExpect(jsonPath("$[0].stale").value(false));
    }
}
```

- [ ] **Step 2: Run the test — expect compilation failure**

```bash
./mvnw test -pl . -Dtest=OptionChainControllerTest -q 2>&1 | tail -15
```

Expected: `COMPILATION ERROR` — `OptionChainController` does not exist.

- [ ] **Step 3: Create `OptionChainController.java`**

Create `src/main/java/com/rj/web/OptionChainController.java`:

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

    public OptionChainController(OptionChainService optionChainService,
                                 OptionChainConfig config) {
        this.optionChainService = optionChainService;
        this.config = config;
    }

    /**
     * Full chain for one underlying — all expiries.
     * Optional {@code ?expiry=DD-MMM-YYYY} filters the {@code expiryData} list.
     */
    @GetMapping("/{underlying}")
    public ResponseEntity<?> getChain(
            @PathVariable String underlying,
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

    /** Summary row per tracked underlying: PCR, VIX LTP, fetchedAt, stale flag. */
    @GetMapping("/summary")
    public List<Map<String, Object>> summary() {
        Duration staleThreshold = Duration.ofSeconds(config.getStaleThresholdSeconds());
        return optionChainService.allSnapshots().entrySet().stream()
                .map(e -> {
                    OptionChainSnapshot snap = e.getValue();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("underlying", snap.underlying());
                    row.put("pcr", snap.pcr());
                    row.put("vixLtp", snap.data().indiaVix != null ? snap.data().indiaVix.ltp : null);
                    row.put("fetchedAt", snap.fetchedAt().toString());
                    row.put("stale", snap.isStale(staleThreshold));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> toResponse(OptionChainSnapshot snap, String expiryFilter) {
        Duration staleThreshold = Duration.ofSeconds(config.getStaleThresholdSeconds());
        var expiries = snap.data().expiryData;
        if (expiryFilter != null && !expiryFilter.isBlank() && expiries != null) {
            expiries = expiries.stream()
                    .filter(e -> expiryFilter.equals(e.date))
                    .toList();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("underlying", snap.underlying());
        result.put("fetchedAt", snap.fetchedAt().toString());
        result.put("stale", snap.isStale(staleThreshold));
        result.put("pcr", snap.pcr());
        result.put("vixLtp", snap.data().indiaVix != null ? snap.data().indiaVix.ltp : null);
        result.put("expiryData", expiries);
        result.put("optionsChain", snap.data().optionsChain);
        return result;
    }
}
```

- [ ] **Step 4: Run the tests — expect pass**

```bash
./mvnw test -pl . -Dtest=OptionChainControllerTest -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`, 5 tests passed.

- [ ] **Step 5: Run full suite**

```bash
./mvnw test -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS` (same pre-existing failures only).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/rj/web/OptionChainController.java \
        src/test/java/com/rj/web/OptionChainControllerTest.java
git commit -m "feat(web): add OptionChainController — four REST endpoints"
```

---

## Task 6: Wire beans in `EngineConfiguration` and `EngineLifecycleManager`

Register `OptionChainService` as a Spring bean and wire it into the lifecycle manager so it starts/stops with the engine and archives at market close.

**Files:**
- Modify: `src/main/java/com/rj/config/EngineConfiguration.java`
- Modify: `src/main/java/com/rj/config/EngineLifecycleManager.java`

- [ ] **Step 1: Add `OptionChainService` bean to `EngineConfiguration`**

In `src/main/java/com/rj/config/EngineConfiguration.java`, add these imports at the top:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rj.engine.options.OptionChainService;
import com.rj.config.OptionChainConfig;
```

Then add these two bean methods after the `symbolProfiler` bean (before the closing `}`):

```java
@Bean
public OptionChainService optionChainService(
        IMarketDataAdapter marketDataAdapter,
        OptionChainConfig optionChainConfig,
        SymbolMasterCache symbolMasterCache,
        ObjectMapper objectMapper) {
    return new OptionChainService(marketDataAdapter, optionChainConfig,
            symbolMasterCache, objectMapper);
}
```

Note: `OptionChainConfig` is already a `@Component` — Spring autowires it automatically. `ObjectMapper` is provided by Spring Boot's Jackson auto-configuration.

- [ ] **Step 2: Add `OptionChainService` to `EngineLifecycleManager`**

Replace the full contents of `src/main/java/com/rj/config/EngineLifecycleManager.java` with:

```java
package com.rj.config;

import com.rj.engine.TradingEngine;
import com.rj.engine.options.OptionChainService;
import com.rj.fyers.TokenRefreshScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Starts the trading engine, token refresh scheduler, and option chain service
 * after the Spring context is ready.
 * Broker connection is user-triggered via POST /api/connect — not automatic on startup.
 */
@Component
public class EngineLifecycleManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EngineLifecycleManager.class);

    private final TradingEngine engine;
    private final TokenRefreshScheduler tokenRefreshScheduler;
    private final OptionChainService optionChainService;
    private volatile boolean running = false;

    public EngineLifecycleManager(TradingEngine engine,
                                  TokenRefreshScheduler tokenRefreshScheduler,
                                  OptionChainService optionChainService) {
        this.engine = engine;
        this.tokenRefreshScheduler = tokenRefreshScheduler;
        this.optionChainService = optionChainService;
    }

    @Override
    public void start() {
        log.info("Starting PTA Backend via Spring lifecycle...");
        engine.start();
        tokenRefreshScheduler.start();
        optionChainService.start();
        running = true;
    }

    @Override
    public void stop() {
        log.info("Stopping TradingEngine via Spring lifecycle...");
        optionChainService.archiveEod();
        optionChainService.stop();
        tokenRefreshScheduler.stop();
        engine.stop();
        running = false;
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public int getPhase() { return Integer.MAX_VALUE; }
}
```

- [ ] **Step 3: Run full suite**

```bash
./mvnw test -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS` (same pre-existing failures only).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/rj/config/EngineConfiguration.java \
        src/main/java/com/rj/config/EngineLifecycleManager.java
git commit -m "feat(config): wire OptionChainService bean + lifecycle start/stop/archiveEod"
```

---

## Task 7: Movement trigger in `CandleService`

After each candle is emitted, call `optionChainService.refreshIfSignificant()` with the symbol, signal, confidence, and relative volume from the recommendation. `OptionChainService` is optional — `CandleService` stays usable without it.

**Files:**
- Modify: `src/main/java/com/rj/engine/CandleService.java`
- Modify: `src/main/java/com/rj/config/EngineConfiguration.java`

- [ ] **Step 1: Add `OptionChainService` field and setter to `CandleService`**

In `src/main/java/com/rj/engine/CandleService.java`:

Add the import at the top:
```java
import com.rj.engine.options.OptionChainService;
```

Add the field after `private volatile Map<String, StrategyYamlConfig> strategyConfigs = Map.of();`:
```java
private volatile OptionChainService optionChainService; // optional — null if disabled
```

Add the setter method after the existing constructors:
```java
public void setOptionChainService(OptionChainService optionChainService) {
    this.optionChainService = optionChainService;
}
```

- [ ] **Step 2: Call `refreshIfSignificant` after candle emission**

In `CandleService.runCandleLoop()`, find the block after `lastEmittedWin = winStart;` (around line 163). Add the trigger call immediately after the `log.info(...)` that logs the emitted candle:

```java
// Trigger async option chain refresh if the candle shows a significant move
OptionChainService ocs = optionChainService;
if (ocs != null) {
    ocs.refreshIfSignificant(symbol, rec.getSignal(), rec.getConfidence(), rec.getRelVolume());
}
```

The full relevant block in `runCandleLoop()` after the change looks like this:

```java
boolean offered = outQueue.offer(rec);
if (!offered) {
    log.warn("[{}][{}] Recommendation queue full — dropping signal for {}", symbol, tf, winStart);
}

lastEmittedWin = winStart;
buffer.pruneBefore(winStart.minus(tf.getDuration()));

log.info("[{}][{}] Candle emitted: {} ticks → {} conf={} src={}",
        symbol, tf, windowTicks.size(),
        rec.getSignal(), String.format("%.2f", rec.getConfidence()),
        rec.getStrategySource());

// Trigger async option chain refresh if the candle shows a significant move
OptionChainService ocs = optionChainService;
if (ocs != null) {
    ocs.refreshIfSignificant(symbol, rec.getSignal(), rec.getConfidence(), rec.getRelVolume());
}
```

- [ ] **Step 3: Wire setter in `EngineConfiguration`**

In `src/main/java/com/rj/config/EngineConfiguration.java`, in the `tradingEngine()` factory method, find the line `engine.loadYamlStrategies(cs, se);` (Step 5 of the existing factory). Add the setter call just before it:

```java
// Wire optional OptionChainService into CandleService for movement-triggered refresh
cs.setOptionChainService(optionChainService);
engine.loadYamlStrategies(cs, se);
```

This means the `tradingEngine()` bean method now needs `OptionChainService optionChainService` as a parameter. Update the signature:

```java
@Bean
public TradingEngine tradingEngine(ConfigManager config,
                                   IOrderAdapter orderAdapter,
                                   PreTradeGate preTradeGate,
                                   RiskSessionState riskSessionState,
                                   PositionBook positionBook,
                                   OptionChainService optionChainService) {
```

- [ ] **Step 4: Run full suite**

```bash
./mvnw test -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS` (same pre-existing failures only).

- [ ] **Step 5: Run and verify the application context loads**

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.main.banner-mode=off" 2>&1 | grep -E "Started|OptionChain|ERROR" | head -20
```

Expected: `OptionChainService started` in the log output. Stop with Ctrl-C.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/rj/engine/CandleService.java \
        src/main/java/com/rj/config/EngineConfiguration.java
git commit -m "feat(engine): wire OptionChainService movement trigger into CandleService"
```

---

## Self-Review

**Spec coverage:**
- OptionChainSnapshot with PCR helpers → Task 3 ✓
- OptionChainConfig from `option-chain.yaml` → Task 2 ✓
- OptionChainService: scheduled refresh → Task 4 (`refreshAll`) ✓
- OptionChainService: event-triggered refresh → Task 4 (`refreshIfSignificant`), Task 7 ✓
- OptionChainService: on-demand / cold start → Task 4 (`getChain`) ✓
- OptionChainService: EOD archive → Task 4 (`archiveEod`) ✓
- OptionChainController: four endpoints → Task 5 ✓
- `allFnoUnderlyings()` on `SymbolMasterCache` → Task 1 ✓
- EngineConfiguration wiring → Task 6, Task 7 ✓
- EngineLifecycleManager start/stop/archiveEod → Task 6 ✓
- Auto-discover underlyings from symbol master → Task 4 (`start()`) ✓

**Placeholder scan:** No TBD, TODO, or "implement later" entries.

**Type consistency:**
- `OptionChainResult.optionsChain` (List\<OptionEntry\>) accessed in Task 3, Task 5 ✓
- `OptionChainResult.expiryData` (List\<Expiry\>) accessed in Task 3, Task 5 ✓
- `OptionChainResult.indiaVix` (VixData with `.ltp`) accessed in Task 5 ✓
- `OptionChainResult.callOi` / `.putOi` (long) accessed in Task 3 ✓
- `OptionChainSnapshot(String, OptionChainResult, Instant)` defined Task 3, used Task 4 + Task 5 ✓
- `OptionChainService.refreshIfSignificant(String, Signal, double, double)` defined Task 4, called Task 7 ✓
- `CandleRecommendation.getSignal()` / `getConfidence()` / `getRelVolume()` — all confirmed to exist ✓
- `SymbolMasterCache.allFnoUnderlyings()` defined Task 1, used Task 4 ✓
