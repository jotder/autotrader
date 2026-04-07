# Phase 3 — Engine Decomposition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Split `RiskManager` and `PositionMonitor` into focused single-responsibility classes and replace the `TradingEngine.create()` static factory with a Spring `@Bean` hand-written assembly method.

**Architecture:** `RiskManager` → `RiskSessionState` (daily state) + `PreTradeGate` (gate logic). `PositionMonitor` → `PositionBook` (shared state) + `TickRiskProcessor` (hot-path Disruptor handler) + `ScheduledPositionManager` (1s scheduler). `TradingEngine.create()` deleted; `EngineConfiguration.tradingEngine()` becomes the hand-written wiring factory resolving circular deps with setter injection.

**Tech Stack:** Java 25, Spring Boot 3.4.4, Maven 3.9+, LMAX Disruptor, JUnit 5, Mockito.

---

## File Structure

**Create:**
- `src/main/java/com/rj/engine/ExitReason.java` — standalone enum (moved from `PositionMonitor.ExitReason`)
- `src/main/java/com/rj/engine/PositionBook.java` — `ConcurrentHashMap<String, OpenPosition>` wrapper
- `src/main/java/com/rj/engine/risk/RiskSessionState.java` — mutable daily trading state
- `src/main/java/com/rj/engine/risk/PreTradeResult.java` — result record (moved from `RiskManager.PreTradeResult`)
- `src/main/java/com/rj/engine/risk/PreTradeGate.java` — pre-trade check + sizing logic
- `src/main/java/com/rj/engine/TickRiskProcessor.java` — Disruptor `EventHandler<TickEvent>` hot path
- `src/main/java/com/rj/engine/ScheduledPositionManager.java` — 1s scheduler, manual exit, time exits
- `src/test/java/com/rj/engine/risk/RiskSessionStateTest.java`
- `src/test/java/com/rj/engine/risk/PreTradeGateTest.java`
- `src/test/java/com/rj/engine/TickRiskProcessorTest.java`

**Modify:**
- `src/main/java/com/rj/engine/TradingEngine.java` — new constructor, delete `create()`, update call sites and getters
- `src/main/java/com/rj/config/EngineConfiguration.java` — new `@Bean` methods + rewrite `tradingEngine()` factory
- `src/main/java/com/rj/engine/StrategyEvaluator.java` — `PositionMonitor` → `PositionBook`; `signalConsumer` non-final; add `setSignalHandler()`
- `src/main/java/com/rj/engine/HealthMonitor.java` — `PositionMonitor` → `ScheduledPositionManager + PositionBook`
- `src/main/java/com/rj/engine/PositionReconciler.java` — `PositionMonitor` → `PositionBook`
- `src/main/java/com/rj/engine/AnomalyDetector.java` — update `initialize()` signature
- `src/main/java/com/rj/web/RiskController.java` — update all `getRiskManager()` call sites
- `src/main/java/com/rj/web/EngineController.java` — update `getPositionMonitor()` call sites
- `src/main/java/com/rj/web/StatusController.java` — update `getPositionMonitor()` call sites
- `src/main/java/com/rj/model/TradeRecord.java` — `PositionMonitor.ExitReason` → `ExitReason`
- 8 other files: `IOrderExecutor`, `LiveOrderExecutor`, `OrderManager`, `PaperOrderExecutor`, `BacktestOrderExecutor`, `StrategyAnalyzer`, `TradeJournal`, `TradingEngine` — all `PositionMonitor.ExitReason` → `ExitReason`

**Delete:**
- `src/main/java/com/rj/engine/RiskManager.java`
- `src/main/java/com/rj/engine/PositionMonitor.java`
- `src/test/java/com/rj/engine/RiskManagerStrategyOverrideTest.java` — replaced by `PreTradeGateTest`
- `src/test/java/com/rj/engine/RiskManagerDrawdownTest.java` — replaced by `RiskSessionStateTest`

---

### Task 1: Create ExitReason + PositionBook; migrate all ExitReason references

**Files:**
- Create: `src/main/java/com/rj/engine/ExitReason.java`
- Create: `src/main/java/com/rj/engine/PositionBook.java`
- Modify: `TradeRecord`, `IOrderExecutor`, `LiveOrderExecutor`, `OrderManager`, `PaperOrderExecutor`, `BacktestOrderExecutor`, `StrategyAnalyzer`, `TradeJournal`, `TradingEngine`, `AnomalyDetector`
- Modify (test): `OrderManagerTest`

- [x] **Step 1: Create ExitReason.java**

```java
// src/main/java/com/rj/engine/ExitReason.java
package com.rj.engine;

public enum ExitReason {
    STOP_LOSS, TAKE_PROFIT, TRAILING_STOP, FORCE_SQUAREOFF, MANUAL, ANOMALY_FLATTEN
}
```

- [x] **Step 2: Create PositionBook.java**

```java
// src/main/java/com/rj/engine/PositionBook.java
package com.rj.engine;

import com.rj.model.OpenPosition;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class PositionBook {

    private final ConcurrentHashMap<String, OpenPosition> positions = new ConcurrentHashMap<>();

    public void add(OpenPosition position) {
        positions.put(position.getCorrelationId(), position);
    }

    public OpenPosition remove(String correlationId) {
        return positions.remove(correlationId);
    }

    public OpenPosition get(String correlationId) {
        return positions.get(correlationId);
    }

    public Collection<OpenPosition> openPositions() {
        return Collections.unmodifiableCollection(positions.values());
    }

    public Collection<OpenPosition> values() {
        return positions.values();
    }

    public int openPositionCount() {
        return positions.size();
    }

    public boolean hasOpenPosition(String symbol) {
        return positions.values().stream().anyMatch(p -> p.getSymbol().equals(symbol));
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }
}
```

- [x] **Step 3: Migrate PositionMonitor.ExitReason → ExitReason in 10 main source files**

The change in every file is: replace `PositionMonitor.ExitReason` with `ExitReason`, add `import com.rj.engine.ExitReason;`, remove `import com.rj.engine.PositionMonitor;` if no longer needed.

**`src/main/java/com/rj/model/TradeRecord.java`** — 3 changes:
- `import com.rj.engine.PositionMonitor;` → `import com.rj.engine.ExitReason;`
- Field: `private PositionMonitor.ExitReason exitReason;` → `private ExitReason exitReason;`
- Method: `public void close(double exitPx, Instant exitTs, PositionMonitor.ExitReason reason)` → `public void close(double exitPx, Instant exitTs, ExitReason reason)`
- Getter: `public PositionMonitor.ExitReason getExitReason()` → `public ExitReason getExitReason()`

**`src/main/java/com/rj/engine/IOrderExecutor.java`** — 1 change:
- `PositionMonitor.ExitReason reason` → `ExitReason reason`; add `import com.rj.engine.ExitReason;`

**`src/main/java/com/rj/engine/LiveOrderExecutor.java`** — 1 change:
- `PositionMonitor.ExitReason reason` → `ExitReason reason`; add `import com.rj.engine.ExitReason;`

**`src/main/java/com/rj/engine/OrderManager.java`** — 1 change:
- `PositionMonitor.ExitReason reason` → `ExitReason reason`; add `import com.rj.engine.ExitReason;`

**`src/main/java/com/rj/engine/PaperOrderExecutor.java`** — 1 change:
- `PositionMonitor.ExitReason reason` → `ExitReason reason`; add `import com.rj.engine.ExitReason;`

**`src/main/java/com/rj/engine/BacktestOrderExecutor.java`** — 1 change:
- `PositionMonitor.ExitReason reason` → `ExitReason reason`; add `import com.rj.engine.ExitReason;`

**`src/main/java/com/rj/engine/StrategyAnalyzer.java`** — 4 changes:
- Replace all `PositionMonitor.ExitReason.STOP_LOSS`, `.TAKE_PROFIT`, `.TRAILING_STOP`, `.FORCE_SQUAREOFF` with `ExitReason.STOP_LOSS` etc.; add `import com.rj.engine.ExitReason;`

**`src/main/java/com/rj/engine/TradeJournal.java`** — 1 change:
- `PositionMonitor.ExitReason reason` → `ExitReason reason`; add `import com.rj.engine.ExitReason;`

**`src/main/java/com/rj/engine/TradingEngine.java`** — 3 changes:
- Lines 262, 264, 310, 397: `PositionMonitor.ExitReason` → `ExitReason`; add `import com.rj.engine.ExitReason;`

**`src/main/java/com/rj/engine/AnomalyDetector.java`** — 1 change:
- `PositionMonitor.ExitReason.ANOMALY_FLATTEN` → `ExitReason.ANOMALY_FLATTEN`; add `import com.rj.engine.ExitReason;`

- [x] **Step 4: Update OrderManagerTest.java**

In `src/test/java/com/rj/engine/OrderManagerTest.java`:
- Replace all `PositionMonitor.ExitReason.STOP_LOSS` with `ExitReason.STOP_LOSS` etc.
- Add `import com.rj.engine.ExitReason;`
- Remove `import com.rj.engine.PositionMonitor;` if present

- [x] **Step 5: Compile and verify no errors**

```
mvn compile -q
```

Expected: BUILD SUCCESS. `PositionMonitor.ExitReason` inner enum still exists in `PositionMonitor.java` so there are no conflicts — we've just added the standalone enum and migrated callers to it.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/rj/engine/ExitReason.java \
        src/main/java/com/rj/engine/PositionBook.java \
        src/main/java/com/rj/model/TradeRecord.java \
        src/main/java/com/rj/engine/IOrderExecutor.java \
        src/main/java/com/rj/engine/LiveOrderExecutor.java \
        src/main/java/com/rj/engine/OrderManager.java \
        src/main/java/com/rj/engine/PaperOrderExecutor.java \
        src/main/java/com/rj/engine/BacktestOrderExecutor.java \
        src/main/java/com/rj/engine/StrategyAnalyzer.java \
        src/main/java/com/rj/engine/TradeJournal.java \
        src/main/java/com/rj/engine/TradingEngine.java \
        src/main/java/com/rj/engine/AnomalyDetector.java \
        src/test/java/com/rj/engine/OrderManagerTest.java
git commit -m "feat(engine): add ExitReason enum + PositionBook; migrate PositionMonitor.ExitReason references"
```

---

### Task 2: Create RiskSessionState (TDD)

**Files:**
- Create: `src/main/java/com/rj/engine/risk/RiskSessionState.java`
- Create: `src/test/java/com/rj/engine/risk/RiskSessionStateTest.java`

- [x] **Step 1: Create the package directory and write the failing test**

```java
// src/test/java/com/rj/engine/risk/RiskSessionStateTest.java
package com.rj.engine.risk;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyRiskConfig;
import com.rj.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RiskSessionStateTest {

    private RiskSessionState state;
    private RiskConfig riskConfig;

    @BeforeEach
    void setUp() {
        riskConfig = RiskConfig.fromEnvironment(key -> switch (key) {
            case "RISK_INITIAL_CAPITAL_INR" -> "100000";
            case "RISK_MAX_DAILY_LOSS_INR"   -> "5000";
            case "RISK_MAX_DAILY_PROFIT_INR" -> "20000";
            case "RISK_MAX_DRAWDOWN_PCT"      -> "3.0";
            case "RISK_MAX_CONSECUTIVE_LOSSES" -> "5";
            default -> null;
        });
        state = new RiskSessionState(riskConfig);
    }

    @Test
    void recordClosedTrade_losingTrade_incrementsConsecutiveLoss() {
        TradeRecord loss = buildTrade("strat-1", false);
        state.recordClosedTrade(loss);
        assertEquals(1, state.getConsecutiveLosses("strat-1"));
    }

    @Test
    void recordClosedTrade_winningTrade_resetsConsecutiveLossCounter() {
        state.recordClosedTrade(buildTrade("strat-1", false));
        state.recordClosedTrade(buildTrade("strat-1", false));
        state.recordClosedTrade(buildTrade("strat-1", true));
        assertEquals(0, state.getConsecutiveLosses("strat-1"));
    }

    @Test
    void recordClosedTrade_reachingProfitTarget_locksDailyProfit() {
        // PnL required: > 20000 INR
        TradeRecord bigWin = buildTradeWithPnl("strat-1", 21000.0);
        state.recordClosedTrade(bigWin);
        assertTrue(state.isDailyProfitLocked());
    }

    @Test
    void updateCurrentEquity_drawdownExceedsLimit_activatesKillSwitch() {
        // Initial capital = 100000, max drawdown = 3% = 3000 INR loss
        state.updateCurrentEquity(-4000); // 4% loss → exceeds 3%
        assertTrue(state.isKillSwitchActive());
    }

    @Test
    void triggerAnomaly_setsAnomalyModeAndKillSwitch() {
        state.triggerAnomaly("Test anomaly");
        assertTrue(state.isAnomalyMode());
        assertTrue(state.isKillSwitchActive());
        assertEquals("Test anomaly", state.getAnomalyReason());
    }

    @Test
    void acknowledgeAnomaly_clearsAnomalyMode() {
        state.triggerAnomaly("Test");
        boolean cleared = state.acknowledgeAnomaly();
        assertTrue(cleared);
        assertFalse(state.isAnomalyMode());
        assertNull(state.getAnomalyReason());
    }

    @Test
    void applyStrategyRiskOverride_nullOverride_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> state.applyStrategyRiskOverride("strat-1", null));
    }

    @Test
    void resetDay_clearsCounters() {
        state.recordClosedTrade(buildTrade("strat-1", false));
        state.activateKillSwitch("test");
        state.resetDay();
        assertFalse(state.isKillSwitchActive());
        assertEquals(0, state.getConsecutiveLosses("strat-1"));
    }

    private TradeRecord buildTrade(String strategyId, boolean winner) {
        return buildTradeWithPnl(strategyId, winner ? 500.0 : -500.0);
    }

    private TradeRecord buildTradeWithPnl(String strategyId, double pnl) {
        Map<Timeframe, Signal> votes = new EnumMap<>(Timeframe.class);
        TradeRecord tr = new TradeRecord(
                "corr-" + System.nanoTime(), "NSE:SBIN-EQ", strategyId,
                ExecutionMode.PAPER, Signal.BUY,
                100.0, 10, 95.0, 110.0,
                Instant.now(), 1.5, 0.9, votes);
        com.rj.engine.ExitReason reason = pnl >= 0
                ? com.rj.engine.ExitReason.TAKE_PROFIT
                : com.rj.engine.ExitReason.STOP_LOSS;
        tr.close(pnl >= 0 ? 110.0 : 95.0, Instant.now(), reason);
        return tr;
    }
}
```

- [x] **Step 2: Run test to verify it fails**

```
mvn test -pl . -Dtest=RiskSessionStateTest -q 2>&1 | tail -5
```

Expected: FAIL — `RiskSessionState` class not found.

- [x] **Step 3: Create RiskSessionState.java**

```java
// src/main/java/com/rj/engine/risk/RiskSessionState.java
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

    private final ConcurrentHashMap<String, AtomicInteger>    consecutiveLosses    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StrategyRiskConfig> strategyRiskOverrides = new ConcurrentHashMap<>();

    public RiskSessionState(RiskConfig riskConfig) {
        this.riskConfig      = riskConfig;
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
            anomalyReason       = reason;
            anomalyTriggeredAt  = Instant.now();
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

    public boolean isKillSwitchActive()     { return killSwitchActive.get(); }
    public boolean isDailyProfitLocked()    { return dailyProfitLocked.get(); }
    public boolean isAnomalyMode()          { return anomalyMode.get(); }
    public String  getAnomalyReason()       { return anomalyReason; }
    public Instant getAnomalyTriggeredAt()  { return anomalyTriggeredAt; }
    public double  getDailyRealizedPnl()    { return dailyRealizedPnl; }

    public int getConsecutiveLosses(String strategyId) {
        AtomicInteger c = consecutiveLosses.get(strategyId);
        return c == null ? 0 : c.get();
    }

    public StrategyRiskConfig getStrategyRiskOverride(String strategyId) {
        return strategyRiskOverrides.get(strategyId);
    }
}
```

- [x] **Step 4: Run test to verify it passes**

```
mvn test -pl . -Dtest=RiskSessionStateTest -q 2>&1 | tail -5
```

Expected: `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/rj/engine/risk/RiskSessionState.java \
        src/test/java/com/rj/engine/risk/RiskSessionStateTest.java
git commit -m "feat(risk): add RiskSessionState — daily trading state extracted from RiskManager"
```

---

### Task 3: Create PreTradeResult + PreTradeGate (TDD)

**Files:**
- Create: `src/main/java/com/rj/engine/risk/PreTradeResult.java`
- Create: `src/main/java/com/rj/engine/risk/PreTradeGate.java`
- Create: `src/test/java/com/rj/engine/risk/PreTradeGateTest.java`

- [x] **Step 1: Create PreTradeResult.java**

```java
// src/main/java/com/rj/engine/risk/PreTradeResult.java
package com.rj.engine.risk;

public record PreTradeResult(
        boolean approved,
        int     quantity,
        double  stopLoss,
        double  takeProfit,
        String  rejectReason) {
}
```

- [x] **Step 2: Write the failing test**

```java
// src/test/java/com/rj/engine/risk/PreTradeGateTest.java
package com.rj.engine.risk;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyRiskConfig;
import com.rj.config.TradeStrategyConfig;
import com.rj.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreTradeGateTest {

    private static final double CAPITAL = 500_000.0;
    private static final String STRAT = "trend_following";

    private static final Supplier<ZonedDateTime> MARKET_HOURS =
            () -> ZonedDateTime.of(2026, 3, 28, 10, 30, 0, 0, ZoneId.of("Asia/Kolkata"));

    @Mock
    private RiskSessionState riskState;

    private RiskConfig riskConfig;
    private PreTradeGate gate;

    @BeforeEach
    void setUp() {
        riskConfig = RiskConfig.defaults();
        gate = new PreTradeGate(riskConfig, riskState, MARKET_HOURS);
        // Register strategy config so Gate 7 passes
        TradeStrategyConfig stratCfg = new TradeStrategyConfig();
        stratCfg.setStrategyId(STRAT);
        stratCfg.setName("Trend Following");
        stratCfg.setActive(true);
        stratCfg.setAllocationPercentage(100.0);
        stratCfg.setSizingType(com.rj.model.SizingType.VOLATILITY_ATR);
        stratCfg.setRiskPercentage(2.0);
        stratCfg.setAtrMultiplier(2.0);
        gate.updateStrategyConfig(stratCfg);
    }

    @Test
    void gate1_killSwitch_rejects() {
        when(riskState.isKillSwitchActive()).thenReturn(true);
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("Kill switch"));
    }

    @Test
    void gate2_drawdownDoubleCheck_rejects() {
        when(riskState.isKillSwitchActive()).thenReturn(false);
        when(riskState.checkDrawdownAndActivate()).thenReturn(true);
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("Drawdown"));
    }

    @Test
    void gate3_dailyProfitLock_rejects() {
        when(riskState.isKillSwitchActive()).thenReturn(false);
        when(riskState.checkDrawdownAndActivate()).thenReturn(false);
        when(riskState.isDailyProfitLocked()).thenReturn(true);
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("Daily profit target"));
    }

    @Test
    void gate4_dailyLossLimit_rejects() {
        when(riskState.isKillSwitchActive()).thenReturn(false);
        when(riskState.checkDrawdownAndActivate()).thenReturn(false);
        when(riskState.isDailyProfitLocked()).thenReturn(false);
        when(riskState.getDailyRealizedPnl()).thenReturn(-riskConfig.getMaxDailyLossInr() - 1);
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("Daily loss limit"));
    }

    @Test
    void gate6_consecutiveLossLimit_rejects() {
        stubAllClear();
        when(riskState.getStrategyRiskOverride(STRAT)).thenReturn(null);
        when(riskState.getConsecutiveLosses(STRAT))
                .thenReturn(riskConfig.getMaxConsecutiveLossesPerStrategy());
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("consecutive losses"));
    }

    @Test
    void gate7_strategyConfigMissing_rejects() {
        stubAllClear();
        when(riskState.getConsecutiveLosses(STRAT)).thenReturn(0);
        when(riskState.getStrategyRiskOverride(STRAT)).thenReturn(null);
        // Remove strategy config
        PreTradeGate gateNoConfig = new PreTradeGate(riskConfig, riskState, MARKET_HOURS);
        PreTradeResult r = gateNoConfig.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertFalse(r.approved());
        assertTrue(r.rejectReason().contains("Strategy config not found"));
    }

    @Test
    void allGatesPass_returnsApprovedWithPositiveQuantity() {
        stubAllClear();
        when(riskState.getConsecutiveLosses(STRAT)).thenReturn(0);
        when(riskState.getStrategyRiskOverride(STRAT)).thenReturn(null);
        PreTradeResult r = gate.preTradeCheck(buildSignal(100, 95), Collections.emptyList(), CAPITAL);
        assertTrue(r.approved());
        assertTrue(r.quantity() > 0);
    }

    @Test
    void applyStrategyRiskOverride_delegatesToRiskSessionState() {
        StrategyRiskConfig override = new StrategyRiskConfig(2.0, 2.0, 2.0, 1.0, 1.0, 20.0, 100, 3);
        gate.applyStrategyRiskOverride(STRAT, override);
        verify(riskState).applyStrategyRiskOverride(STRAT, override);
    }

    @Test
    void removeStrategyRiskOverride_delegatesToRiskSessionState() {
        gate.removeStrategyRiskOverride(STRAT);
        verify(riskState).removeStrategyRiskOverride(STRAT);
    }

    private void stubAllClear() {
        when(riskState.isKillSwitchActive()).thenReturn(false);
        when(riskState.checkDrawdownAndActivate()).thenReturn(false);
        when(riskState.isDailyProfitLocked()).thenReturn(false);
        when(riskState.getDailyRealizedPnl()).thenReturn(0.0);
    }

    private TradeSignal buildSignal(double entry, double sl) {
        return TradeSignal.builder()
                .symbol("NSE:SBIN-EQ")
                .correlationId("corr-" + System.nanoTime())
                .direction(Signal.BUY)
                .confidence(0.9)
                .strategyId(STRAT)
                .suggestedEntry(entry)
                .suggestedStopLoss(sl)
                .suggestedTarget(entry * 1.10)
                .build();
    }
}
```

- [x] **Step 3: Run test to verify it fails**

```
mvn test -pl . -Dtest=PreTradeGateTest -q 2>&1 | tail -5
```

Expected: FAIL — `PreTradeGate` class not found.

- [x] **Step 4: Create PreTradeGate.java**

```java
// src/main/java/com/rj/engine/risk/PreTradeGate.java
package com.rj.engine.risk;

import com.rj.config.RiskConfig;
import com.rj.config.StrategyRiskConfig;
import com.rj.config.TradeStrategyConfig;
import com.rj.model.OpenPosition;
import com.rj.model.TradeSignal;
import com.rj.risk.sizing.ISizingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PreTradeGate {

    private static final Logger log = LoggerFactory.getLogger(PreTradeGate.class);

    private final RiskConfig       riskConfig;
    private final RiskSessionState riskSessionState;
    private final Supplier<ZonedDateTime> clock;
    private final ConcurrentHashMap<String, TradeStrategyConfig> strategyConfigs = new ConcurrentHashMap<>();

    public PreTradeGate(RiskConfig riskConfig, RiskSessionState riskSessionState) {
        this(riskConfig, riskSessionState,
                () -> ZonedDateTime.now(riskConfig.getExchangeZone()));
    }

    public PreTradeGate(RiskConfig riskConfig, RiskSessionState riskSessionState,
                        Supplier<ZonedDateTime> clock) {
        this.riskConfig       = riskConfig;
        this.riskSessionState = riskSessionState;
        this.clock            = clock;
    }

    private static PreTradeResult reject(String reason) {
        log.info("Pre-trade REJECTED: {}", reason);
        return new PreTradeResult(false, 0, 0, 0, reason);
    }

    public PreTradeResult preTradeCheck(TradeSignal signal,
                                        Collection<OpenPosition> openPositions,
                                        double totalCapital) {
        // Gate 1: kill switch
        if (riskSessionState.isKillSwitchActive()) {
            return reject("Kill switch active — trading halted for the day");
        }

        // Gate 2: immediate drawdown double-check
        if (riskSessionState.checkDrawdownAndActivate()) {
            return reject("Drawdown limit breached — kill switch active");
        }

        // Gate 3: daily profit lock
        if (riskSessionState.isDailyProfitLocked()) {
            return reject("Daily profit target reached (" + riskConfig.getMaxDailyProfitInr()
                    + " INR) — no new entries");
        }

        // Gate 4: daily loss limit
        if (riskSessionState.getDailyRealizedPnl() <= -riskConfig.getMaxDailyLossInr()) {
            riskSessionState.activateKillSwitch("Daily loss limit breached: "
                    + String.format("%.2f", riskSessionState.getDailyRealizedPnl()) + " INR");
            return reject("Daily loss limit breached: "
                    + String.format("%.2f", riskSessionState.getDailyRealizedPnl()) + " INR");
        }

        // Gate 5: time cutoff
        ZonedDateTime now = clock.get();
        if (now.toLocalTime().isAfter(riskConfig.getNoNewTradesAfter())) {
            return reject("Past no-new-trades cutoff " + riskConfig.getNoNewTradesAfter() + " IST");
        }

        // Gate 6: consecutive loss limit per strategy
        StrategyRiskConfig stratOverride = riskSessionState.getStrategyRiskOverride(signal.getStrategyId());
        int maxConsecLosses = stratOverride != null
                ? stratOverride.maxConsecutiveLosses()
                : riskConfig.getMaxConsecutiveLossesPerStrategy();
        int consec = riskSessionState.getConsecutiveLosses(signal.getStrategyId());
        if (consec >= maxConsecLosses) {
            return reject("Strategy [" + signal.getStrategyId() + "] suspended: "
                    + consec + " consecutive losses");
        }

        // Gate 7: max exposure per symbol
        double currentExposure = openPositions.stream()
                .filter(p -> p.getSymbol().equals(signal.getSymbol()))
                .mapToDouble(p -> p.getEntryPrice() * p.getQuantity())
                .sum();
        double maxExposureFraction = stratOverride != null
                ? stratOverride.maxExposurePct() / 100.0
                : riskConfig.getMaxExposurePerSymbolPercent();
        double maxExposure = totalCapital * maxExposureFraction;
        if (currentExposure >= maxExposure) {
            return reject(String.format("Max exposure per symbol exceeded: %.0f >= %.0f",
                    currentExposure, maxExposure));
        }

        // Gate 8: strategy-level capital + sizing
        TradeStrategyConfig stratCfg = strategyConfigs.get(signal.getStrategyId());
        if (stratCfg == null) {
            return reject("Strategy config not found for: " + signal.getStrategyId());
        }

        double strategyCapital = totalCapital * (stratCfg.getAllocationPercentage() / 100.0);
        ISizingModel sizingModel = stratCfg.createSizingModel();
        double rawQty = sizingModel.calculateQuantity(signal, strategyCapital);
        int lotSize = signal.getLotSize();
        int lotAlignedQty = (int) (Math.floor(rawQty / lotSize) * lotSize);

        if (lotAlignedQty == 0 && lotSize > 1 && rawQty > 0) lotAlignedQty = lotSize;

        if (lotAlignedQty <= 0) {
            return reject(String.format("Insufficient strategy capital for [%s] (needed=%.2f qty, lot=%d)",
                    stratCfg.getName(), rawQty, lotSize));
        }

        int maxQtyPerOrder = stratOverride != null
                ? stratOverride.maxQty()
                : riskConfig.getMaxQuantityPerOrder();
        double symbolMaxExposureFraction = stratOverride != null
                ? stratOverride.maxExposurePct() / 100.0
                : riskConfig.getMaxExposurePerSymbolPercent();
        double symbolMaxExposure = totalCapital * symbolMaxExposureFraction;
        int exposureCapQty = (int) Math.floor(
                (symbolMaxExposure - currentExposure) / signal.getSuggestedEntry());

        int finalQty = Math.min(lotAlignedQty, Math.min(maxQtyPerOrder, exposureCapQty));
        if (lotSize > 1) finalQty = (finalQty / lotSize) * lotSize;

        if (finalQty <= 0) {
            return reject(String.format("Quantity 0 after caps (lotAligned=%d, maxPerOrder=%d, exposureCap=%d)",
                    lotAlignedQty, maxQtyPerOrder, exposureCapQty));
        }

        log.info("[{}] Pre-trade OK: qty={} sizingModel={} strategyCap={} reason={}",
                signal.getSymbol(), finalQty, sizingModel.getName(),
                String.format("%.2f", strategyCapital), signal.getReason());

        return new PreTradeResult(true, finalQty,
                signal.getSuggestedStopLoss(), signal.getSuggestedTarget(), null);
    }

    public void updateStrategyConfig(TradeStrategyConfig config) {
        strategyConfigs.put(config.getStrategyId(), config);
        log.info("Updated PreTradeGate config for strategy: {} [{}% capital]",
                config.getStrategyId(), config.getAllocationPercentage());
    }

    public void applyStrategyRiskOverride(String strategyId, StrategyRiskConfig override) {
        riskSessionState.applyStrategyRiskOverride(strategyId, override);
    }

    public void removeStrategyRiskOverride(String strategyId) {
        riskSessionState.removeStrategyRiskOverride(strategyId);
    }
}
```

- [x] **Step 5: Run test to verify it passes**

```
mvn test -pl . -Dtest=PreTradeGateTest -q 2>&1 | tail -5
```

Expected: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/rj/engine/risk/PreTradeResult.java \
        src/main/java/com/rj/engine/risk/PreTradeGate.java \
        src/test/java/com/rj/engine/risk/PreTradeGateTest.java
git commit -m "feat(risk): add PreTradeGate + PreTradeResult extracted from RiskManager"
```

---

### Task 4: Create TickRiskProcessor (TDD)

**Files:**
- Create: `src/main/java/com/rj/engine/TickRiskProcessor.java`
- Create: `src/test/java/com/rj/engine/TickRiskProcessorTest.java`

- [x] **Step 1: Write the failing test**

```java
// src/test/java/com/rj/engine/TickRiskProcessorTest.java
package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.engine.disruptor.TickEvent;
import com.rj.engine.risk.RiskSessionState;
import com.rj.model.OpenPosition;
import com.rj.model.Signal;
import com.rj.model.Tick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TickRiskProcessorTest {

    @Mock private RiskSessionState riskState;

    private PositionBook positionBook;
    private RiskConfig riskConfig;
    private TickRiskProcessor processor;

    private final AtomicReference<ExitReason> capturedReason = new AtomicReference<>();
    private BiConsumer<OpenPosition, ExitReason> exitHandler;

    @BeforeEach
    void setUp() {
        riskConfig   = RiskConfig.defaults();
        positionBook = new PositionBook();
        processor    = new TickRiskProcessor(positionBook, riskState, riskConfig);
        exitHandler  = (pos, reason) -> capturedReason.set(reason);
        processor.setExitHandler(exitHandler);
    }

    @Test
    void slHit_callsExitHandlerWithStopLoss() {
        OpenPosition pos = new OpenPosition(
                "NSE:SBIN-EQ", "corr-1", "strat", Signal.BUY,
                100.0, 10, 95.0, 110.0, Instant.now());
        positionBook.add(pos);

        when(riskState.isKillSwitchActive()).thenReturn(false);
        fireEvent("NSE:SBIN-EQ", 94.0); // below SL of 95

        assertEquals(ExitReason.STOP_LOSS, capturedReason.get());
        assertTrue(positionBook.isEmpty(), "Position must be removed after exit");
    }

    @Test
    void tpHit_callsExitHandlerWithTakeProfit() {
        OpenPosition pos = new OpenPosition(
                "NSE:SBIN-EQ", "corr-2", "strat", Signal.BUY,
                100.0, 10, 95.0, 110.0, Instant.now());
        positionBook.add(pos);

        when(riskState.isKillSwitchActive()).thenReturn(false);
        fireEvent("NSE:SBIN-EQ", 111.0); // above TP of 110

        assertEquals(ExitReason.TAKE_PROFIT, capturedReason.get());
    }

    @Test
    void tickForUnwatchedSymbol_isNoOp() {
        OpenPosition pos = new OpenPosition(
                "NSE:SBIN-EQ", "corr-3", "strat", Signal.BUY,
                100.0, 10, 95.0, 110.0, Instant.now());
        positionBook.add(pos);

        when(riskState.isKillSwitchActive()).thenReturn(false);
        fireEvent("NSE:RELIANCE-EQ", 50.0); // different symbol

        assertNull(capturedReason.get(), "No exit for different symbol");
    }

    @Test
    void killSwitchActiveAndNotAnomalyMode_returnsEarly() {
        OpenPosition pos = new OpenPosition(
                "NSE:SBIN-EQ", "corr-4", "strat", Signal.BUY,
                100.0, 10, 95.0, 110.0, Instant.now());
        positionBook.add(pos);

        when(riskState.isKillSwitchActive()).thenReturn(true);
        when(riskState.isAnomalyMode()).thenReturn(false);
        fireEvent("NSE:SBIN-EQ", 94.0); // would be SL hit

        assertNull(capturedReason.get(), "No exit when kill switch is active without anomaly mode");
    }

    private void fireEvent(String symbol, double price) {
        Tick tick = new Tick(symbol, price);
        TickEvent event = new TickEvent();
        event.setTick(tick);
        processor.onEvent(event, 0, true);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

```
mvn test -pl . -Dtest=TickRiskProcessorTest -q 2>&1 | tail -5
```

Expected: FAIL — `TickRiskProcessor` class not found.

- [x] **Step 3: Create TickRiskProcessor.java**

```java
// src/main/java/com/rj/engine/TickRiskProcessor.java
package com.rj.engine;

import com.lmax.disruptor.EventHandler;
import com.rj.config.RiskConfig;
import com.rj.engine.disruptor.TickEvent;
import com.rj.engine.risk.RiskSessionState;
import com.rj.model.OpenPosition;
import com.rj.model.Signal;
import com.rj.model.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * Disruptor EventHandler — hot path SL/TP/trailing-stop check on every tick.
 * No scheduling, no manual control — only real-time price checks.
 */
public class TickRiskProcessor implements EventHandler<TickEvent> {

    private static final Logger log = LoggerFactory.getLogger(TickRiskProcessor.class);

    private final PositionBook     positionBook;
    private final RiskSessionState riskSessionState;
    private final RiskConfig       riskConfig;

    private volatile BiConsumer<OpenPosition, ExitReason> exitHandler;
    private volatile StrategyEvaluator strategyEvaluator;

    public TickRiskProcessor(PositionBook positionBook,
                             RiskSessionState riskSessionState,
                             RiskConfig riskConfig) {
        this.positionBook     = positionBook;
        this.riskSessionState = riskSessionState;
        this.riskConfig       = riskConfig;
    }

    public void setExitHandler(BiConsumer<OpenPosition, ExitReason> exitHandler) {
        this.exitHandler = exitHandler;
    }

    public void setStrategyEvaluator(StrategyEvaluator strategyEvaluator) {
        this.strategyEvaluator = strategyEvaluator;
    }

    // ── HOT PATH ─────────────────────────────────────────────────────────────

    @Override
    public void onEvent(TickEvent event, long sequence, boolean endOfBatch) {
        if (positionBook.isEmpty()) return;
        if (riskSessionState.isKillSwitchActive() && !riskSessionState.isAnomalyMode()) return;

        Tick tick = event.getTick();
        if (tick == null) return;

        String symbol       = tick.getSymbol();
        double currentPrice = tick.getLtp();

        for (OpenPosition pos : positionBook.values()) {
            if (pos.getSymbol().equals(symbol)) {
                checkRisk(pos, currentPrice);
            }
        }
    }

    private void checkRisk(OpenPosition pos, double price) {
        if (pos.isStopLossHit(price)) {
            log.info("[{}] Real-time SL hit: price={} sl={}", pos.getSymbol(), price, pos.getCurrentStopLoss());
            closePosition(pos, ExitReason.STOP_LOSS);
            return;
        }
        if (pos.isTakeProfitHit(price)) {
            log.info("[{}] Real-time TP hit: price={} tp={}", pos.getSymbol(), price, pos.getTakeProfit());
            closePosition(pos, ExitReason.TAKE_PROFIT);
            return;
        }
        updateTrailingStop(pos, price);
    }

    private void updateTrailingStop(OpenPosition pos, double price) {
        double pnlPct = pos.getDirection() == Signal.BUY
                ? (price - pos.getEntryPrice()) / pos.getEntryPrice()
                : (pos.getEntryPrice() - price) / pos.getEntryPrice();

        if (!pos.isTrailingActivated() && pnlPct >= riskConfig.getTrailingActivationPercent()) {
            pos.setTrailingActivated(true);
            log.info("[{}] Trailing stop activated at price={}", pos.getSymbol(), price);
        }

        if (!pos.isTrailingActivated()) return;

        pos.updateHighWaterMark(price);
        double stepPct = riskConfig.getTrailingStepPercent();
        double newStop = pos.getDirection() == Signal.BUY
                ? pos.getHighWaterMark() * (1.0 - stepPct)
                : pos.getHighWaterMark() * (1.0 + stepPct);

        if (pos.stepTrailingStop(newStop)) {
            log.info("[{}] Trailing stop moved to {}", pos.getSymbol(), pos.getCurrentStopLoss());
            if (pos.isStopLossHit(price)) {
                closePosition(pos, ExitReason.TRAILING_STOP);
            }
        }
    }

    private void closePosition(OpenPosition pos, ExitReason reason) {
        positionBook.remove(pos.getCorrelationId());
        log.info("[{}] Closing position reason={}: {}", pos.getSymbol(), reason, pos);
        try {
            if (exitHandler != null) exitHandler.accept(pos, reason);
        } catch (Exception e) {
            log.error("[{}] Exit handler failed: {}", pos.getSymbol(), e.getMessage());
        }
        if (strategyEvaluator != null) strategyEvaluator.onPositionClosed(pos.getSymbol());
    }
}
```

- [x] **Step 4: Run test to verify it passes**

```
mvn test -pl . -Dtest=TickRiskProcessorTest -q 2>&1 | tail -5
```

Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/rj/engine/TickRiskProcessor.java \
        src/test/java/com/rj/engine/TickRiskProcessorTest.java
git commit -m "feat(engine): add TickRiskProcessor — hot-path SL/TP handler extracted from PositionMonitor"
```

---

### Task 5: Create ScheduledPositionManager

**Files:**
- Create: `src/main/java/com/rj/engine/ScheduledPositionManager.java`

- [x] **Step 1: Create ScheduledPositionManager.java**

```java
// src/main/java/com/rj/engine/ScheduledPositionManager.java
package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.engine.risk.RiskSessionState;
import com.rj.model.OpenPosition;
import com.rj.model.Tick;
import com.rj.model.TickStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * 1-second scheduler for time-based exits, drawdown propagation, and manual exit control.
 * Not on the hot path — all operations run in the scheduler thread.
 */
public class ScheduledPositionManager {

    private static final Logger log = LoggerFactory.getLogger(ScheduledPositionManager.class);

    private final PositionBook     positionBook;
    private final RiskSessionState riskSessionState;
    private final RiskConfig       riskConfig;
    private final TickStore        tickStore;

    private volatile BiConsumer<OpenPosition, ExitReason> exitHandler;
    private volatile StrategyEvaluator strategyEvaluator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    public ScheduledPositionManager(PositionBook positionBook,
                                    RiskSessionState riskSessionState,
                                    RiskConfig riskConfig,
                                    TickStore tickStore) {
        this.positionBook     = positionBook;
        this.riskSessionState = riskSessionState;
        this.riskConfig       = riskConfig;
        this.tickStore        = tickStore;
    }

    public void setExitHandler(BiConsumer<OpenPosition, ExitReason> exitHandler) {
        this.exitHandler = exitHandler;
    }

    public void setStrategyEvaluator(StrategyEvaluator strategyEvaluator) {
        this.strategyEvaluator = strategyEvaluator;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("ScheduledPositionManager already running");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("position-time-monitor").factory());
        scheduler.scheduleAtFixedRate(this::scheduledRiskMaintenance, 1, 1, TimeUnit.SECONDS);
        log.info("ScheduledPositionManager started (Real-time Disruptor + 1s Time monitor)");
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (scheduler != null) scheduler.shutdownNow();
        log.info("ScheduledPositionManager stopped. {} positions still open",
                positionBook.openPositionCount());
    }

    public boolean isRunning() {
        return running.get();
    }

    // ── Manual control ────────────────────────────────────────────────────────

    public void requestManualExit(String correlationId) {
        OpenPosition pos = positionBook.get(correlationId);
        if (pos == null) {
            throw new IllegalArgumentException("No open position with correlationId: " + correlationId);
        }
        closePosition(pos, ExitReason.MANUAL);
    }

    public int closeAllPositions(ExitReason reason) {
        int count = 0;
        for (OpenPosition pos : positionBook.values()) {
            closePosition(pos, reason);
            count++;
        }
        return count;
    }

    // ── Scheduled maintenance ─────────────────────────────────────────────────

    private void scheduledRiskMaintenance() {
        if (positionBook.isEmpty()) {
            riskSessionState.updateCurrentEquity(0);
            return;
        }

        // 1. Compute total open PnL and propagate to risk state
        double totalOpenPnL = 0;
        for (OpenPosition pos : positionBook.openPositions()) {
            Tick lastTick = tickStore.getLastTick(pos.getSymbol());
            if (lastTick != null) totalOpenPnL += pos.unrealizedPnl(lastTick.getLtp());
        }
        riskSessionState.updateCurrentEquity(totalOpenPnL);

        // 2. Anomaly flatten
        if (riskSessionState.isAnomalyMode() && !positionBook.isEmpty()) {
            log.warn("Anomaly detected — Auto-flattening {} positions", positionBook.openPositionCount());
            closeAllPositions(ExitReason.ANOMALY_FLATTEN);
            return;
        }

        // 3. Time-based force square-off
        ZonedDateTime now = ZonedDateTime.now(riskConfig.getExchangeZone());
        if (now.toLocalTime().compareTo(riskConfig.getMarketCloseTime()) >= 0) {
            log.warn("Market close reached — forcing square-off of {} positions",
                    positionBook.openPositionCount());
            closeAllPositions(ExitReason.FORCE_SQUAREOFF);
        }
    }

    private void closePosition(OpenPosition pos, ExitReason reason) {
        positionBook.remove(pos.getCorrelationId());
        log.info("[{}] Closing position reason={}: {}", pos.getSymbol(), reason, pos);
        try {
            if (exitHandler != null) exitHandler.accept(pos, reason);
        } catch (Exception e) {
            log.error("[{}] Exit handler failed: {}", pos.getSymbol(), e.getMessage());
        }
        if (strategyEvaluator != null) strategyEvaluator.onPositionClosed(pos.getSymbol());
    }
}
```

- [x] **Step 2: Compile**

```
mvn compile -q
```

Expected: BUILD SUCCESS.

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/rj/engine/ScheduledPositionManager.java
git commit -m "feat(engine): add ScheduledPositionManager — 1s scheduler extracted from PositionMonitor"
```

---

### Task 6: Update StrategyEvaluator (PositionMonitor → PositionBook; add setSignalHandler)

**Files:**
- Modify: `src/main/java/com/rj/engine/StrategyEvaluator.java`
- Modify: `src/test/java/com/rj/engine/StrategyEvaluatorYamlTest.java`

- [x] **Step 1: Update StrategyEvaluator.java**

Make the following changes in `src/main/java/com/rj/engine/StrategyEvaluator.java`:

**Change the import** (top of file):
- Remove: `import com.rj.engine.PositionMonitor;`
- Add: `import com.rj.engine.PositionBook;`

**Change the field** (line ~44):
```java
// Before:
private final PositionMonitor positionMonitor;

// After:
private PositionBook positionBook;         // non-final for compatibility
```

Also make `signalConsumer` non-final:
```java
// Before:
private final Consumer<TradeSignal> signalConsumer;

// After:
private Consumer<TradeSignal> signalConsumer;
```

**Change the constructor** (line ~56–65):
```java
// Before:
public StrategyEvaluator(BlockingQueue<CandleRecommendation> inQueue,
                         Consumer<TradeSignal> signalConsumer,
                         RiskConfig riskConfig,
                         PositionMonitor positionMonitor) {
    this.inQueue = inQueue;
    this.signalConsumer = signalConsumer;
    this.riskConfig = riskConfig;
    this.positionMonitor = positionMonitor;
    this.signalJournal = new SignalJournal();
}

// After:
public StrategyEvaluator(BlockingQueue<CandleRecommendation> inQueue,
                         Consumer<TradeSignal> signalConsumer,
                         RiskConfig riskConfig,
                         PositionBook positionBook) {
    this.inQueue = inQueue;
    this.signalConsumer = signalConsumer;
    this.riskConfig = riskConfig;
    this.positionBook = positionBook;
    this.signalJournal = new SignalJournal();
}
```

**Add setter** (after the constructor):
```java
public void setSignalHandler(Consumer<TradeSignal> handler) {
    this.signalConsumer = handler;
}
```

**Change `isExecutionAllowed()`** (line ~188):
```java
// Before:
if (positionMonitor.hasOpenPosition(symbol)) {

// After:
if (positionBook != null && positionBook.hasOpenPosition(symbol)) {
```

**Change `evaluateSymbol()` signal dispatch** (line ~160):
```java
// Before:
signalConsumer.accept(sig);

// After:
if (signalConsumer != null) signalConsumer.accept(sig);
```

- [x] **Step 2: Update StrategyEvaluatorYamlTest.java**

In `src/test/java/com/rj/engine/StrategyEvaluatorYamlTest.java`:
- Remove the `PositionMonitor positionMonitor` field
- Change construction: wherever `new PositionMonitor(...)` is used, replace with `new PositionBook()`
- Update `StrategyEvaluator` construction: `new StrategyEvaluator(queue, handler, riskConfig, positionBook)`

The test currently has approximately:
```java
// Before:
positionMonitor = new PositionMonitor(null, riskConfig, riskManager, (p, r) -> {}, null);
// ...
new StrategyEvaluator(queue, handler, riskConfig, positionMonitor)

// After:
PositionBook positionBook = new PositionBook();
// ...
new StrategyEvaluator(queue, handler, riskConfig, positionBook)
```

Remove all `RiskManager riskManager = new RiskManager(...)` if the test only used it to construct `PositionMonitor`.

- [x] **Step 3: Compile and run StrategyEvaluator tests**

```
mvn test -pl . -Dtest=StrategyEvaluatorYamlTest -q 2>&1 | tail -5
```

Expected: same number of passes as before this task (0 failures).

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/rj/engine/StrategyEvaluator.java \
        src/test/java/com/rj/engine/StrategyEvaluatorYamlTest.java
git commit -m "feat(engine): StrategyEvaluator — PositionMonitor → PositionBook; add setSignalHandler"
```

---

### Task 7: Update HealthMonitor + PositionReconciler

**Files:**
- Modify: `src/main/java/com/rj/engine/HealthMonitor.java`
- Modify: `src/main/java/com/rj/engine/PositionReconciler.java`
- Modify: `src/test/java/com/rj/engine/PositionReconcilerTest.java`

- [x] **Step 1: Update HealthMonitor.java**

In `src/main/java/com/rj/engine/HealthMonitor.java`:

**Change imports:** add `import com.rj.engine.ScheduledPositionManager;` and `import com.rj.engine.PositionBook;`; remove `import com.rj.engine.PositionMonitor;`

**Change fields** (around line 44):
```java
// Before:
private final PositionMonitor positionMonitor;

// After:
private final ScheduledPositionManager scheduledPositionManager;
private final PositionBook positionBook;
```

**Change constructor** (around line 51–60):
```java
// Before:
public HealthMonitor(TickStore tickStore,
                     CandleService candleService,
                     StrategyEvaluator strategyEvaluator,
                     PositionMonitor positionMonitor,
                     String[] activeSymbols) {
    this.tickStore = tickStore;
    this.candleService = candleService;
    this.strategyEvaluator = strategyEvaluator;
    this.positionMonitor = positionMonitor;
    this.activeSymbols = activeSymbols;
}

// After:
public HealthMonitor(TickStore tickStore,
                     CandleService candleService,
                     StrategyEvaluator strategyEvaluator,
                     ScheduledPositionManager scheduledPositionManager,
                     PositionBook positionBook,
                     String[] activeSymbols) {
    this.tickStore = tickStore;
    this.candleService = candleService;
    this.strategyEvaluator = strategyEvaluator;
    this.scheduledPositionManager = scheduledPositionManager;
    this.positionBook = positionBook;
    this.activeSymbols = activeSymbols;
}
```

**Change `checkPositionMonitor()`**:
```java
// Before:
private void checkPositionMonitor() {
    boolean alive = positionMonitor.isRunning();
    int openCount = positionMonitor.openPositionCount();
    if (!alive) {
        log.error("[HealthMonitor] POSITIONS Monitor is NOT running! {} positions unmonitored", openCount);
    } else {
        log.info("[HealthMonitor] POSITIONS Monitor running — {} open positions", openCount);
        if (openCount > 0) {
            positionMonitor.openPositions().forEach(pos -> log.info("[HealthMonitor]   → {}", pos));
        }
    }
}

// After:
private void checkPositionMonitor() {
    boolean alive = scheduledPositionManager.isRunning();
    int openCount = positionBook.openPositionCount();
    if (!alive) {
        log.error("[HealthMonitor] POSITIONS Monitor is NOT running! {} positions unmonitored", openCount);
    } else {
        log.info("[HealthMonitor] POSITIONS Monitor running — {} open positions", openCount);
        if (openCount > 0) {
            positionBook.openPositions().forEach(pos -> log.info("[HealthMonitor]   → {}", pos));
        }
    }
}
```

- [x] **Step 2: Update PositionReconciler.java**

In `src/main/java/com/rj/engine/PositionReconciler.java`:

**Change imports:** add `import com.rj.engine.PositionBook;`; remove `import com.rj.engine.PositionMonitor;`

**Change field** (line ~39):
```java
// Before:
private final PositionMonitor positionMonitor;

// After:
private final PositionBook positionBook;
```

**Change both constructors** — replace `PositionMonitor positionMonitor` with `PositionBook positionBook` in both constructor signatures and assignments.

**Change `reconcile()`** (line ~95):
```java
// Before:
for (OpenPosition ep : positionMonitor.openPositions()) {

// After:
for (OpenPosition ep : positionBook.openPositions()) {
```

**Change stale position removal** (line ~151):
```java
// Before:
positionMonitor.removePosition(stale.getCorrelationId());

// After:
positionBook.remove(stale.getCorrelationId());
```

**Change `adoptBrokerPosition()`** (line ~207):
```java
// Before:
positionMonitor.addPosition(position);

// After:
positionBook.add(position);
```

- [x] **Step 3: Update PositionReconcilerTest.java**

In `src/test/java/com/rj/engine/PositionReconcilerTest.java`:

Remove `RiskManager riskManager = new RiskManager(...)` and `PositionMonitor positionMonitor = new PositionMonitor(...)` construction. Replace with:
```java
PositionBook positionBook = new PositionBook();
```

Update `PositionReconciler` construction: wherever `positionMonitor` is passed, pass `positionBook` instead.

- [x] **Step 4: Compile and run tests**

```
mvn test -pl . -Dtest=PositionReconcilerTest -q 2>&1 | tail -5
```

Expected: same pass count as before (0 new failures).

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/rj/engine/HealthMonitor.java \
        src/main/java/com/rj/engine/PositionReconciler.java \
        src/test/java/com/rj/engine/PositionReconcilerTest.java
git commit -m "feat(engine): HealthMonitor + PositionReconciler — PositionMonitor → PositionBook/ScheduledPositionManager"
```

---

### Task 8: Update AnomalyDetector + AnomalyDetectorTest

**Files:**
- Modify: `src/main/java/com/rj/engine/AnomalyDetector.java`
- Modify: `src/test/java/com/rj/engine/AnomalyDetectorTest.java`

- [x] **Step 1: Update AnomalyDetector.java**

In `src/main/java/com/rj/engine/AnomalyDetector.java`:

**Change imports:** add `import com.rj.engine.risk.RiskSessionState;` and `import com.rj.engine.ScheduledPositionManager;`; remove `import com.rj.engine.RiskManager;` and `import com.rj.engine.PositionMonitor;`

**Change fields** (around line 37–39):
```java
// Before:
private RiskManager riskManager;
private PositionMonitor positionMonitor;

// After:
private RiskSessionState riskSessionState;
private ScheduledPositionManager scheduledPositionManager;
```

**Change `initialize()` signature** (line 53):
```java
// Before:
public void initialize(RiskManager riskManager, PositionMonitor positionMonitor,
                       TickStore tickStore, TradeJournal journal, RiskConfig riskConfig) {
    this.riskManager = riskManager;
    this.positionMonitor = positionMonitor;

// After:
public void initialize(RiskSessionState riskSessionState,
                       ScheduledPositionManager scheduledPositionManager,
                       TickStore tickStore, TradeJournal journal, RiskConfig riskConfig) {
    this.riskSessionState = riskSessionState;
    this.scheduledPositionManager = scheduledPositionManager;
```

**Change `check()` method guard** (line 85):
```java
// Before:
if (triggered.get() || riskManager == null || riskManager.isAnomalyMode()) return;

// After:
if (triggered.get() || riskSessionState == null || riskSessionState.isAnomalyMode()) return;
```

**Change `checkDrawdown()`** (around line 108–115):
```java
// Before:
double pnl = riskManager.getDailyRealizedPnl();

// After:
double pnl = riskSessionState.getDailyRealizedPnl();
```

**Change `trigger()`** (around line 175–180):
```java
// Before:
riskManager.triggerAnomaly(reason);
// ...
int closed = positionMonitor.closeAllPositions(PositionMonitor.ExitReason.ANOMALY_FLATTEN);
// ...
journal.log("ANOMALY_FLATTEN", java.util.Map.of(
        "reason", reason,
        "closedCount", closed,
        "pnlAtTrigger", riskManager.getDailyRealizedPnl()
));

// After:
riskSessionState.triggerAnomaly(reason);
// ...
int closed = scheduledPositionManager.closeAllPositions(ExitReason.ANOMALY_FLATTEN);
// ...
journal.log("ANOMALY_FLATTEN", java.util.Map.of(
        "reason", reason,
        "closedCount", closed,
        "pnlAtTrigger", riskSessionState.getDailyRealizedPnl()
));
```

- [x] **Step 2: Update AnomalyDetectorTest.java**

In `src/test/java/com/rj/engine/AnomalyDetectorTest.java`:

The test currently constructs `RiskManager` and a `PositionMonitor` anonymous subclass. Replace both:

```java
// Before fields:
private RiskManager riskManager;
private PositionMonitor positionMonitor;

// After fields:
private RiskSessionState riskSessionState;
private ScheduledPositionManager scheduledPositionManager;
```

In `@BeforeEach setup()`:
```java
// Before:
riskManager = new RiskManager(riskConfig);
positionMonitor = new PositionMonitor(null, riskConfig, riskManager, (p, r) -> {}, null) {
    @Override
    public int closeAllPositions(ExitReason reason) { return closedCount.get(); }
    @Override
    public void start() {}
    // ...
};

// After:
riskSessionState = new RiskSessionState(riskConfig);
// Use a real ScheduledPositionManager with empty PositionBook — closeAllPositions returns 0
ScheduledPositionManager spm = new ScheduledPositionManager(
        new PositionBook(), riskSessionState, riskConfig, null);
scheduledPositionManager = spm;
```

Update `detector.initialize(...)` call:
```java
// Before:
detector.initialize(riskManager, positionMonitor, tickStore, journal, riskConfig);

// After:
detector.initialize(riskSessionState, scheduledPositionManager, tickStore, journal, riskConfig);
```

Update any test assertions that previously used `riskManager.isAnomalyMode()` → `riskSessionState.isAnomalyMode()`.

- [x] **Step 3: Compile and run AnomalyDetector tests**

```
mvn test -pl . -Dtest=AnomalyDetectorTest -q 2>&1 | tail -5
```

Expected: same pass count as before (0 new failures).

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/rj/engine/AnomalyDetector.java \
        src/test/java/com/rj/engine/AnomalyDetectorTest.java
git commit -m "feat(engine): AnomalyDetector — RiskManager → RiskSessionState; PositionMonitor → ScheduledPositionManager"
```

---

### Task 9: Rewrite TradingEngine (constructor + internal call sites + getters)

**Files:**
- Modify: `src/main/java/com/rj/engine/TradingEngine.java`

- [x] **Step 1: Update imports**

In `src/main/java/com/rj/engine/TradingEngine.java`, replace:
```java
// Remove:
import com.rj.engine.RiskManager;
import com.rj.engine.PositionMonitor;

// Add:
import com.rj.engine.risk.PreTradeGate;
import com.rj.engine.risk.PreTradeResult;
import com.rj.engine.risk.RiskSessionState;
import com.rj.engine.ExitReason;
import com.rj.engine.PositionBook;
import com.rj.engine.TickRiskProcessor;
import com.rj.engine.ScheduledPositionManager;
```

- [x] **Step 2: Replace private fields**

```java
// Remove these fields:
private final RiskManager riskManager;
private CandleService candleService;
private StrategyEvaluator strategyEvaluator;
private PositionMonitor positionMonitor;
private HealthMonitor healthMonitor;
private PositionReconciler positionReconciler;
private ConfigFileWatcher configFileWatcher;
private AnomalyDetector anomalyDetector;
private BrokerCircuitBreaker circuitBreaker;
private final ConcurrentHashMap<String, TradeRecord> openRecords = new ConcurrentHashMap<>();

// Replace with:
private final PreTradeGate preTradeGate;
private final RiskSessionState riskSessionState;
private final PositionBook positionBook;
private final TickRiskProcessor tickRiskProcessor;
private final ScheduledPositionManager scheduledPositionManager;
private final StrategyEvaluator strategyEvaluator;
private final CandleService candleService;
private final AnomalyDetector anomalyDetector;
private final BrokerCircuitBreaker circuitBreaker;
private final HealthMonitor healthMonitor;
private final PositionReconciler positionReconciler;   // nullable
private final ConcurrentHashMap<String, TradeRecord> openRecords;
private ConfigFileWatcher configFileWatcher;           // set during loadYamlStrategies
```

- [x] **Step 3: Replace private constructor + delete static factory**

Delete the old private constructor and the entire `create()` static factory. Add:

```java
TradingEngine(ExecutionMode mode, IOrderExecutor executor, OrderManager orderManager,
              PreTradeGate preTradeGate, RiskSessionState riskSessionState,
              PositionBook positionBook, TickRiskProcessor tickRiskProcessor,
              ScheduledPositionManager scheduledPositionManager,
              StrategyEvaluator strategyEvaluator, CandleService candleService,
              AnomalyDetector anomalyDetector, BrokerCircuitBreaker circuitBreaker,
              HealthMonitor healthMonitor, PositionReconciler positionReconciler,
              TradeJournal journal, ConfigManager config,
              TickDisruptorEngine disruptorEngine, FyersSocketListener socketListener,
              ConcurrentHashMap<String, TradeRecord> openRecords) {
    this.mode = mode;
    this.executor = executor;
    this.orderManager = orderManager;
    this.preTradeGate = preTradeGate;
    this.riskSessionState = riskSessionState;
    this.positionBook = positionBook;
    this.tickRiskProcessor = tickRiskProcessor;
    this.scheduledPositionManager = scheduledPositionManager;
    this.strategyEvaluator = strategyEvaluator;
    this.candleService = candleService;
    this.anomalyDetector = anomalyDetector;
    this.circuitBreaker = circuitBreaker;
    this.healthMonitor = healthMonitor;
    this.positionReconciler = positionReconciler;
    this.journal = journal;
    this.config = config;
    this.disruptorEngine = disruptorEngine;
    this.socketListener = socketListener;
    this.openRecords = openRecords;
}
```

- [x] **Step 4: Update handleSignal()**

```java
private void handleSignal(TradeSignal signal) {
    log.info("[{}] Signal received: {}", signal.getSymbol(), signal);
    journal.logSignalGenerated(signal);

    PreTradeResult check = preTradeGate.preTradeCheck(
            signal, positionBook.openPositions(), config.getRiskConfig().getInitialCapitalInr());

    if (!check.approved()) {
        log.info("[{}] Signal REJECTED: {}", signal.getSymbol(), check.rejectReason());
        journal.logSignalRejected(signal, check.rejectReason());
        return;
    }

    double entryAtr = Math.abs(signal.getSuggestedEntry() - signal.getSuggestedStopLoss()) / 2.0;
    TradeRecord record = new TradeRecord(
            signal.getCorrelationId(), signal.getSymbol(), signal.getStrategyId(),
            mode, signal.getDirection(), 0, 0, check.stopLoss(), check.takeProfit(),
            Instant.now(), entryAtr, signal.getConfidence(), signal.getTimeframeVotes());
    openRecords.put(signal.getCorrelationId(), record);
    orderManager.submitEntry(signal, check.quantity());
}
```

- [x] **Step 5: Update handleEntryFilled(), handleExitFilled(), handleExit()**

```java
private void handleEntryFilled(ManagedOrder order) {
    log.info("[{}] ENTRY FILLED: {} @ {}", order.getSymbol(), order.getFilledQuantity(), order.getFillPrice());
    TradeRecord record = openRecords.get(order.getCorrelationId());
    double sl = record != null ? record.getInitialStopLoss() : order.getFillPrice() * 0.99;
    double tp = record != null ? record.getTakeProfit() : order.getFillPrice() * 1.02;
    OpenPosition pos = new OpenPosition(
            order.getSymbol(), order.getCorrelationId(), order.getStrategyId(),
            order.getDirection(), order.getFillPrice(), order.getFilledQuantity(),
            sl, tp, order.getLastUpdatedAt());
    positionBook.add(pos);
    journal.logOrderEntry(null, order.toOrderFill());
}

private void handleExitFilled(ManagedOrder order) {
    log.info("[{}] EXIT FILLED: {} @ {}", order.getSymbol(), order.getFilledQuantity(), order.getFillPrice());
    TradeRecord record = openRecords.remove(order.getCorrelationId());
    if (record != null) {
        ExitReason reason = ExitReason.MANUAL;
        if (order.getRejectReason() != null && order.getRejectReason().startsWith("reason=")) {
            try { reason = ExitReason.valueOf(order.getRejectReason().substring(7)); }
            catch (Exception ignored) {}
        }
        record.close(order.getFillPrice(), order.getLastUpdatedAt(), reason);
        riskSessionState.recordClosedTrade(record);
        journal.logTradeClosed(record);
        log.info("[{}] Trade CLOSED: pnl={} R={}", order.getSymbol(),
                String.format("%.2f", record.getPnl()),
                String.format("%.2f", record.getRMultipleAchieved()));
    }
}

private void handleExit(OpenPosition position, ExitReason reason) {
    double triggerPrice = switch (reason) {
        case STOP_LOSS, TRAILING_STOP -> position.getCurrentStopLoss();
        case TAKE_PROFIT              -> position.getTakeProfit();
        default                       -> 0;
    };
    log.info("[{}] Exit triggered: reason={} price={}", position.getSymbol(), reason, triggerPrice);
    orderManager.submitExit(position, reason, triggerPrice);
}
```

- [x] **Step 6: Update start() and stop()**

```java
public void start() {
    if (!running.compareAndSet(false, true)) return;
    log.info("TradingEngine starting in {} mode...", mode);
    if (positionReconciler != null) positionReconciler.reconcile();
    disruptorEngine.start();
    scheduledPositionManager.start();
    anomalyDetector.start();
    strategyEvaluator.start();
    candleService.start(config.getActiveSymbols());
    healthMonitor.start();
    if (mode != ExecutionMode.BACKTEST) {
        socketListener.startWebSocket();
        socketListener.subscribe(java.util.Arrays.asList(config.getActiveSymbols()));
    }
    if (configFileWatcher != null) try { configFileWatcher.start(); } catch (IOException ignored) {}
    registerShutdownHook();
}

public void stop() {
    if (!running.compareAndSet(true, false)) return;
    log.info("TradingEngine stopping...");
    if (configFileWatcher != null) configFileWatcher.stop();
    socketListener.close();
    orderManager.shutdown();
    healthMonitor.stop();
    candleService.stop();
    strategyEvaluator.stop();
    anomalyDetector.stop();
    scheduledPositionManager.stop();
    disruptorEngine.stop();
}
```

- [x] **Step 7: Update flattenAll(), loadYamlStrategies(), initializePluggableStrategies()**

```java
public int flattenAll(String reason) {
    riskSessionState.triggerAnomaly(reason);
    return scheduledPositionManager.closeAllPositions(ExitReason.ANOMALY_FLATTEN);
}
```

For `loadYamlStrategies()` and `initializePluggableStrategies()`: remove the `RiskManager riskMgr` parameter from both signatures. The methods now use `this.preTradeGate` directly. Change all `riskMgr.applyStrategyRiskOverride(...)` → `preTradeGate.applyStrategyRiskOverride(...)` and `riskMgr.updateStrategyConfig(...)` → `preTradeGate.updateStrategyConfig(...)`. Change signatures to take no riskMgr param (they receive CandleService and StrategyEvaluator only, since preTradeGate is a field):

```java
private void loadYamlStrategies(CandleService cs, StrategyEvaluator se) { ... }
private void initializePluggableStrategies(StrategyEvaluator se) { ... }
```

- [x] **Step 8: Replace old getters with new ones**

```java
// Remove:
public RiskManager getRiskManager() { return riskManager; }
public PositionMonitor getPositionMonitor() { return positionMonitor; }

// Add:
public PreTradeGate getPreTradeGate() { return preTradeGate; }
public RiskSessionState getRiskSessionState() { return riskSessionState; }
public PositionBook getPositionBook() { return positionBook; }
public ScheduledPositionManager getScheduledPositionManager() { return scheduledPositionManager; }
```

Keep all other existing getters unchanged (`getJournal()`, `getOrderTracker()`, `getCircuitBreaker()`, `getDisruptorEngine()`, `getSocketListener()`, `getHealthMonitor()`, `getCandleService()`, `getStrategyEvaluator()`, `getPositionReconciler()`, `getAnomalyDetector()`, `isRunning()`, `getMode()`).

- [x] **Step 9: Compile — expect errors only from EngineConfiguration**

```
mvn compile -q 2>&1 | grep "error:" | head -20
```

Expected: errors only in `EngineConfiguration.java` (still calls `TradingEngine.create()`). No other errors.

- [x] **Step 10: Commit**

```bash
git add src/main/java/com/rj/engine/TradingEngine.java
git commit -m "feat(engine): TradingEngine — delete static factory; new constructor; update all call sites"
```

---

### Task 10: Rewrite EngineConfiguration @Bean factory

**Files:**
- Modify: `src/main/java/com/rj/config/EngineConfiguration.java`

- [x] **Step 1: Rewrite EngineConfiguration.java completely**

```java
// src/main/java/com/rj/config/EngineConfiguration.java
package com.rj.config;

import com.rj.broker.IMarketDataAdapter;
import com.rj.broker.IOrderAdapter;
import com.rj.engine.*;
import com.rj.engine.disruptor.TickDisruptorEngine;
import com.rj.engine.disruptor.TickStoreUpdater;
import com.rj.engine.risk.PreTradeGate;
import com.rj.engine.risk.RiskSessionState;
import com.rj.fyers.FyersSocketListener;
import com.rj.model.*;
import com.tts.in.model.FyersClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Configuration
public class EngineConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EngineConfiguration.class);
    private static final int REC_QUEUE_CAPACITY = 2048;

    @Bean
    public FyersClass fyersClass() {
        return FyersClass.getInstance();
    }

    @Bean
    public TickStore tickStore() {
        return TickStore.getInstance();
    }

    @Bean
    public RiskConfig riskConfig(ConfigManager configManager) {
        return configManager.getRiskConfig();
    }

    @Bean
    public StrategyConfig strategyConfig(ConfigManager configManager) {
        return configManager.getStrategyConfig();
    }

    // ── Phase 3: new split beans ─────────────────────────────────────────────

    @Bean
    public RiskSessionState riskSessionState(RiskConfig riskConfig) {
        return new RiskSessionState(riskConfig);
    }

    @Bean
    public PreTradeGate preTradeGate(RiskConfig riskConfig, RiskSessionState riskSessionState) {
        return new PreTradeGate(riskConfig, riskSessionState);
    }

    @Bean
    public PositionBook positionBook() {
        return new PositionBook();
    }

    // ── Main engine factory ──────────────────────────────────────────────────

    @Bean
    public TradingEngine tradingEngine(ConfigManager config,
                                       IOrderAdapter orderAdapter,
                                       PreTradeGate preTradeGate,
                                       RiskSessionState riskSessionState,
                                       PositionBook positionBook) {

        ExecutionMode mode = resolveMode(config.getProperty("APP_ENV"));
        TickStore tickStore = TickStore.getInstance();
        RiskConfig riskConfig = config.getRiskConfig();

        IOrderExecutor executor = createExecutor(mode, tickStore, orderAdapter);
        TradeJournal journal = new TradeJournal(mode);
        TickDisruptorEngine disruptor = new TickDisruptorEngine();

        ConcurrentHashMap<String, TradeRecord> openRecords = new ConcurrentHashMap<>();
        OrderTracker orderTracker = new OrderTracker(Duration.ofSeconds(30));
        OrderManager orderManager = new OrderManager(executor, orderTracker, journal);

        // FyersSocketListener has final OrderManager field — create after OrderManager
        FyersSocketListener socketListener = new FyersSocketListener(disruptor, orderManager);

        LinkedBlockingQueue<CandleRecommendation> recQueue =
                new LinkedBlockingQueue<>(REC_QUEUE_CAPACITY);

        // Step 1: construct components that don't yet have all callbacks
        TickRiskProcessor tickRiskProcessor =
                new TickRiskProcessor(positionBook, riskSessionState, riskConfig);
        ScheduledPositionManager scheduledPositionManager =
                new ScheduledPositionManager(positionBook, riskSessionState, riskConfig, tickStore);
        StrategyEvaluator se = new StrategyEvaluator(recQueue, null, riskConfig, positionBook);
        CandleService cs = new CandleService(tickStore, recQueue, config);

        AnomalyDetector ad = new AnomalyDetector();
        CircuitBreakerConfig cbConfig =
                CircuitBreakerConfig.fromEnvironment(config::getProperty);
        BrokerCircuitBreaker cb = new BrokerCircuitBreaker(cbConfig, ad);
        if (executor instanceof LiveOrderExecutor loe) loe.setCircuitBreaker(cb);

        HealthMonitor hm = new HealthMonitor(tickStore, cs, se,
                scheduledPositionManager, positionBook, config.getActiveSymbols());

        PositionReconciler reconciler = null;
        if (mode == ExecutionMode.LIVE) {
            reconciler = new PositionReconciler(
                    orderAdapter, positionBook, openRecords, journal, riskConfig);
        }

        // Step 2: create TradingEngine with all deps
        TradingEngine engine = new TradingEngine(
                mode, executor, orderManager,
                preTradeGate, riskSessionState, positionBook,
                tickRiskProcessor, scheduledPositionManager,
                se, cs, ad, cb, hm, reconciler,
                journal, config, disruptor, socketListener, openRecords);

        // Step 3: setter injection to break circular deps (engine callbacks)
        tickRiskProcessor.setExitHandler(engine::handleExit);
        tickRiskProcessor.setStrategyEvaluator(se);
        scheduledPositionManager.setExitHandler(engine::handleExit);
        scheduledPositionManager.setStrategyEvaluator(se);
        se.setSignalHandler(engine::handleSignal);

        // Step 4: Disruptor handlers + OMS listener
        disruptor.addHandler(new TickStoreUpdater());
        disruptor.addHandler(tickRiskProcessor);
        orderTracker.addListener(engine);

        // Step 5: AnomalyDetector init + strategy loading
        ad.initialize(riskSessionState, scheduledPositionManager, tickStore, journal, riskConfig);
        engine.loadYamlStrategies(cs, se);
        engine.initializePluggableStrategies(se);

        log.info("TradingEngine created — mode={} symbols={}",
                mode, String.join(",", config.getActiveSymbols()));
        return engine;
    }

    // ── Existing @Beans that extract from TradingEngine ──────────────────────

    @Bean
    public TickDisruptorEngine tickDisruptorEngine(TradingEngine tradingEngine) {
        return tradingEngine.getDisruptorEngine();
    }

    @Bean
    public FyersSocketListener fyersSocketListener(TradingEngine tradingEngine) {
        return tradingEngine.getSocketListener();
    }

    @Bean
    public DimensionDataCache dimensionDataCache() {
        return DimensionDataCache.load(Path.of("data/dim"));
    }

    @Bean
    public SymbolMasterCache symbolMasterCache() {
        return SymbolMasterCache.load(Path.of("data/symbol_master"));
    }

    @Bean
    public CandleDatabase candleDatabase() {
        return new CandleDatabase(Path.of("data/history"));
    }

    @Bean
    public SymbolProfiler symbolProfiler(CandleDatabase candleDatabase) {
        return new SymbolProfiler(candleDatabase);
    }

    @Bean
    public BrokerCircuitBreaker brokerCircuitBreaker(TradingEngine tradingEngine) {
        return tradingEngine.getCircuitBreaker();
    }

    @Bean
    public CandleDownloader candleDownloader(
            IMarketDataAdapter marketDataAdapter,
            CandleDatabase candleDatabase,
            BrokerCircuitBreaker circuitBreaker) {
        return new CandleDownloader(marketDataAdapter, candleDatabase, 500, circuitBreaker);
    }

    @Bean
    public DownloadTracker downloadTracker(CandleDownloader candleDownloader) {
        return new DownloadTracker(candleDownloader);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ExecutionMode resolveMode(String appEnv) {
        if (appEnv == null) return ExecutionMode.PAPER;
        return switch (appEnv.trim().toUpperCase()) {
            case "LIVE"     -> ExecutionMode.LIVE;
            case "BACKTEST" -> ExecutionMode.BACKTEST;
            default         -> ExecutionMode.PAPER;
        };
    }

    private static IOrderExecutor createExecutor(ExecutionMode mode,
                                                  TickStore tickStore,
                                                  IOrderAdapter orderAdapter) {
        return switch (mode) {
            case LIVE     -> new LiveOrderExecutor(orderAdapter);
            case BACKTEST -> new BacktestOrderExecutor();
            default       -> new PaperOrderExecutor(tickStore);
        };
    }
}
```

Note: `engine.loadYamlStrategies(cs, se)` and `engine.initializePluggableStrategies(se)` are package-accessible methods on TradingEngine. Make them package-private (remove `private` modifier) so `EngineConfiguration` can call them since they are in different packages. Alternatively, make them `public`. Use `public` for simplicity.

- [x] **Step 2: Make loadYamlStrategies and initializePluggableStrategies public in TradingEngine**

In `src/main/java/com/rj/engine/TradingEngine.java`:
```java
// Before:
private void loadYamlStrategies(CandleService cs, StrategyEvaluator se) { ... }
private void initializePluggableStrategies(StrategyEvaluator se) { ... }

// After:
public void loadYamlStrategies(CandleService cs, StrategyEvaluator se) { ... }
public void initializePluggableStrategies(StrategyEvaluator se) { ... }
```

Also update references inside `loadYamlStrategies()` from `riskMgr.X(...)` to `preTradeGate.X(...)` (the field, not a param).

- [x] **Step 3: Compile**

```
mvn compile -q
```

Expected: BUILD SUCCESS.

- [x] **Step 4: Run all tests**

```
mvn test -q 2>&1 | tail -10
```

Expected: same pre-existing failures only (CandleDatabaseTest ×6, RiskManagerStrategyOverrideTest failures, FnoRiskSizingTest failures, CandleAggregationTest ×1). No new failures.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/rj/config/EngineConfiguration.java \
        src/main/java/com/rj/engine/TradingEngine.java
git commit -m "feat(engine): EngineConfiguration — replace TradingEngine.create() with @Bean factory; wire all split classes"
```

---

### Task 11: Update web controllers

**Files:**
- Modify: `src/main/java/com/rj/web/RiskController.java`
- Modify: `src/main/java/com/rj/web/EngineController.java`
- Modify: `src/main/java/com/rj/web/StatusController.java`
- Modify: `src/test/java/com/rj/web/StatusControllerTest.java`

- [x] **Step 1: Rewrite RiskController.java**

```java
// src/main/java/com/rj/web/RiskController.java
package com.rj.web;

import com.rj.config.ConfigManager;
import com.rj.engine.AnomalyDetector;
import com.rj.engine.TradingEngine;
import com.rj.engine.risk.PreTradeResult;
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
        var state = engine.getRiskSessionState();
        var cfg   = configManager.getRiskConfig();
        return new RiskResponse(
                state.getDailyRealizedPnl(),
                state.isKillSwitchActive(),
                state.isDailyProfitLocked(),
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
        var state  = engine.getRiskSessionState();
        var result = new LinkedHashMap<String, Object>();
        result.put("anomalyMode",       state.isAnomalyMode());
        result.put("reason",            state.getAnomalyReason());
        result.put("triggeredAt",       state.getAnomalyTriggeredAt());
        result.put("killSwitchActive",  state.isKillSwitchActive());
        AnomalyDetector detector = engine.getAnomalyDetector();
        if (detector != null) {
            result.put("detectorTriggered",        detector.isTriggered());
            result.put("consecutiveBrokerErrors",  detector.getConsecutiveBrokerErrors());
        }
        return result;
    }

    @PostMapping("/anomaly/acknowledge")
    public ActionResponse acknowledgeAnomaly() {
        boolean cleared = engine.getRiskSessionState().acknowledgeAnomaly();
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
    public ActionResponse kill(
            @RequestParam(defaultValue = "Manual kill via REST API") String reason) {
        engine.getRiskSessionState().activateKillSwitch(reason);
        return new ActionResponse(true, "Kill switch activated: " + reason);
    }

    @PostMapping("/reset")
    public ActionResponse reset() {
        engine.getRiskSessionState().resetDay();
        return new ActionResponse(true, "Daily risk state reset");
    }
}
```

- [x] **Step 2: Update EngineController.java**

In `src/main/java/com/rj/web/EngineController.java`:

**Line 35** (positions endpoint):
```java
// Before:
return engine.getPositionMonitor().openPositions();

// After:
return engine.getPositionBook().openPositions();
```

**Lines 69–73** (exit endpoint):
```java
// Before:
PositionMonitor pm = engine.getPositionMonitor();
try {
    pm.requestManualExit(correlationId);

// After:
try {
    engine.getScheduledPositionManager().requestManualExit(correlationId);
```

Remove the `import com.rj.engine.PositionMonitor;` import.

- [x] **Step 3: Update StatusController.java**

In `src/main/java/com/rj/web/StatusController.java`:

```java
// Before:
"positionMonitorRunning", engine.getPositionMonitor().isRunning(),
// ...
"openPositionCount",      engine.getPositionMonitor().openPositionCount(),

// After:
"positionMonitorRunning", engine.getScheduledPositionManager().isRunning(),
// ...
"openPositionCount",      engine.getPositionBook().openPositionCount(),
```

Remove `import com.rj.engine.PositionMonitor;`.

- [x] **Step 4: Update StatusControllerTest.java**

In `src/test/java/com/rj/web/StatusControllerTest.java`, the test mocks `engine.getPositionMonitor()`. Update:

```java
// Before:
PositionMonitor pm = mock(PositionMonitor.class);
when(engine.getPositionMonitor()).thenReturn(pm);
when(pm.isRunning()).thenReturn(true);
when(pm.openPositionCount()).thenReturn(0);

// After:
ScheduledPositionManager spm = mock(ScheduledPositionManager.class);
PositionBook pb = mock(PositionBook.class);
when(engine.getScheduledPositionManager()).thenReturn(spm);
when(engine.getPositionBook()).thenReturn(pb);
when(spm.isRunning()).thenReturn(true);
when(pb.openPositionCount()).thenReturn(0);
```

Add imports: `import com.rj.engine.ScheduledPositionManager;` and `import com.rj.engine.PositionBook;`; remove `import com.rj.engine.PositionMonitor;`.

- [x] **Step 5: Compile and run web controller tests**

```
mvn test -pl . -Dtest="StatusControllerTest,StrategyControllerTest,EngineControllerConnectTest" -q 2>&1 | tail -5
```

Expected: all pass (0 failures).

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/rj/web/RiskController.java \
        src/main/java/com/rj/web/EngineController.java \
        src/main/java/com/rj/web/StatusController.java \
        src/test/java/com/rj/web/StatusControllerTest.java
git commit -m "feat(web): update RiskController, EngineController, StatusController — RiskManager/PositionMonitor → split classes"
```

---

### Task 12: Delete RiskManager + PositionMonitor; update remaining tests; final compile + test

**Files:**
- Delete: `src/main/java/com/rj/engine/RiskManager.java`
- Delete: `src/main/java/com/rj/engine/PositionMonitor.java`
- Delete: `src/test/java/com/rj/engine/RiskManagerStrategyOverrideTest.java`
- Delete: `src/test/java/com/rj/engine/RiskManagerDrawdownTest.java`
- Modify: `src/test/java/com/rj/engine/FnoRiskSizingTest.java`

- [x] **Step 1: Delete the four files**

```bash
rm src/main/java/com/rj/engine/RiskManager.java
rm src/main/java/com/rj/engine/PositionMonitor.java
rm src/test/java/com/rj/engine/RiskManagerStrategyOverrideTest.java
rm src/test/java/com/rj/engine/RiskManagerDrawdownTest.java
```

- [x] **Step 2: Rewrite FnoRiskSizingTest.java**

The test must now construct `PreTradeGate` with a `RiskSessionState` and register a `TradeStrategyConfig` for the `"test"` strategy so Gate 7 passes.

```java
// src/test/java/com/rj/engine/FnoRiskSizingTest.java
package com.rj.engine;

import com.rj.config.RiskConfig;
import com.rj.config.TradeStrategyConfig;
import com.rj.engine.risk.PreTradeGate;
import com.rj.engine.risk.PreTradeResult;
import com.rj.engine.risk.RiskSessionState;
import com.rj.model.*;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class FnoRiskSizingTest {

    private static final Supplier<ZonedDateTime> MARKET_HOURS_CLOCK =
            () -> ZonedDateTime.of(2026, 3, 28, 10, 30, 0, 0, ZoneId.of("Asia/Kolkata"));

    private static PreTradeGate gateFor(RiskConfig cfg) {
        RiskSessionState state = new RiskSessionState(cfg);
        PreTradeGate gate = new PreTradeGate(cfg, state, MARKET_HOURS_CLOCK);
        // Register "test" strategy config so Gate 7 passes
        TradeStrategyConfig stratCfg = new TradeStrategyConfig();
        stratCfg.setStrategyId("test");
        stratCfg.setName("Test Strategy");
        stratCfg.setActive(true);
        stratCfg.setAllocationPercentage(100.0);
        stratCfg.setSizingType(SizingType.VOLATILITY_ATR);
        stratCfg.setRiskPercentage(2.0);
        stratCfg.setAtrMultiplier(2.0);
        gate.updateStrategyConfig(stratCfg);
        return gate;
    }

    @Test
    void equitySignalSizesInShares() {
        PreTradeGate gate = gateFor(testRiskConfig("1000000"));
        var signal = TradeSignal.builder()
                .symbol("NSE:SBIN-EQ")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(500)
                .suggestedStopLoss(490)
                .suggestedTarget(520)
                .strategyId("test")
                .vote(Timeframe.M5, Signal.BUY)
                .build();
        PreTradeResult result = gate.preTradeCheck(signal, Collections.emptyList(), 1_000_000);
        assertTrue(result.approved(), "Should be approved during market hours");
        assertEquals(1000, result.quantity());
    }

    @Test
    void futureSignalSizesInLots() {
        PreTradeGate gate = gateFor(testRiskConfig("5000000"));
        var signal = TradeSignal.builder()
                .symbol("NSE:NIFTY26MARFUT")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(22000)
                .suggestedStopLoss(21900)
                .suggestedTarget(22200)
                .strategyId("test")
                .vote(Timeframe.M5, Signal.BUY)
                .instrumentInfo(InstrumentInfo.derivative(SymbolType.EQUITY_FUTURE, 25, "FO"))
                .build();
        PreTradeResult result = gate.preTradeCheck(signal, Collections.emptyList(), 5_000_000);
        assertTrue(result.approved(), "Should be approved during market hours");
        assertTrue(result.quantity() > 0);
        assertEquals(0, result.quantity() % 25, "Quantity must be multiple of lot size 25");
    }

    @Test
    void futureMinimumOneLot() {
        PreTradeGate gate = gateFor(testRiskConfig("1000000"));
        var signal = TradeSignal.builder()
                .symbol("NSE:NIFTY26MARFUT")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(5000)
                .suggestedStopLoss(4900)
                .suggestedTarget(5200)
                .strategyId("test")
                .vote(Timeframe.M5, Signal.BUY)
                .instrumentInfo(InstrumentInfo.derivative(SymbolType.EQUITY_FUTURE, 75, "FO"))
                .build();
        PreTradeResult result = gate.preTradeCheck(signal, Collections.emptyList(), 1_000_000);
        assertTrue(result.approved());
        assertEquals(75, result.quantity());
        assertEquals(0, result.quantity() % 75);
    }

    @Test
    void futureSignalMultipleLots() {
        PreTradeGate gate = gateFor(testRiskConfig("1000000"));
        var signal = TradeSignal.builder()
                .symbol("NSE:BANKNIFTY26MARFUT")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(100)
                .suggestedStopLoss(90)
                .suggestedTarget(120)
                .strategyId("test")
                .vote(Timeframe.M5, Signal.BUY)
                .instrumentInfo(InstrumentInfo.derivative(SymbolType.EQUITY_FUTURE, 50, "FO"))
                .build();
        PreTradeResult result = gate.preTradeCheck(signal, Collections.emptyList(), 1_000_000);
        assertTrue(result.approved());
        assertEquals(1000, result.quantity());
        assertEquals(0, result.quantity() % 50);
    }

    @Test
    void optionSignalUsesMarginProductType() {
        var signal = TradeSignal.builder()
                .symbol("NSE:NIFTY26OCT22000CE")
                .direction(Signal.BUY)
                .confidence(0.8)
                .suggestedEntry(200)
                .suggestedStopLoss(180)
                .suggestedTarget(250)
                .strategyId("test")
                .instrumentInfo(InstrumentInfo.derivative(SymbolType.EQUITY_OPTION_MONTHLY, 25, "FO"))
                .build();
        assertEquals("MARGIN", signal.getProductType());
        assertEquals(25, signal.getLotSize());
    }

    private static RiskConfig testRiskConfig(String capital) {
        return RiskConfig.fromEnvironment(key -> switch (key) {
            case "RISK_INITIAL_CAPITAL_INR"       -> capital;
            case "RISK_MAX_DAILY_LOSS_INR"        -> "50000";
            case "RISK_MAX_DAILY_PROFIT_INR"      -> "100000";
            case "RISK_MAX_PER_TRADE_PCT"         -> "0.02";
            case "RISK_MAX_EXPOSURE_PER_SYMBOL_PCT" -> "0.50";
            case "RISK_MAX_QTY_PER_ORDER"         -> "1000";
            case "RISK_MAX_CONSECUTIVE_LOSSES"    -> "5";
            case "RISK_NO_NEW_TRADES_AFTER"       -> "15:00";
            case "RISK_MARKET_CLOSE_TIME"         -> "15:15";
            case "RISK_TRAILING_ACTIVATION_PCT"   -> "0.015";
            case "RISK_TRAILING_STEP_PCT"         -> "0.005";
            default -> null;
        });
    }
}
```

- [x] **Step 3: Full compile**

```
mvn compile -q
```

Expected: BUILD SUCCESS (all `RiskManager` and `PositionMonitor` references are gone from main source).

- [x] **Step 4: Run all tests**

```
mvn test -q 2>&1 | grep -E "Tests run:|BUILD"
```

Expected: `Tests run: 280+, Failures: 0, Errors: 0` for all pre-existing-failure test classes now resolved. The `FnoRiskSizingTest` 4 sizing tests now PASS (they were pre-existing failures because of missing strategy config). `CandleAggregationTest`, `CandleDatabaseTest` failures remain (unrelated to this phase).

- [x] **Step 5: Commit**

```bash
git add src/test/java/com/rj/engine/FnoRiskSizingTest.java
git commit -m "feat(engine): delete RiskManager + PositionMonitor; fix FnoRiskSizingTest with PreTradeGate — Phase 3 complete"
```

---

## Self-Review

**Spec coverage:**
- ✅ `RiskSessionState` — Task 2
- ✅ `PreTradeGate` + `PreTradeResult` — Task 3
- ✅ `PositionBook` — Task 1
- ✅ `TickRiskProcessor` — Task 4
- ✅ `ScheduledPositionManager` — Task 5
- ✅ `ExitReason` standalone enum — Task 1
- ✅ `TradingEngine` constructor + delete `create()` — Task 9
- ✅ `EngineConfiguration` factory — Task 10
- ✅ `StrategyEvaluator` update — Task 6
- ✅ `HealthMonitor` update — Task 7
- ✅ `PositionReconciler` update — Task 7
- ✅ `AnomalyDetector` update — Task 8
- ✅ Web controller updates — Task 11
- ✅ Delete `RiskManager`, `PositionMonitor` — Task 12
- ✅ `FyersSocketListener` circular dep resolution — Task 10 (no setter needed; OrderManager created before socketListener)
- ✅ `StrategyEvaluator.setSignalHandler()` — Task 6

**Placeholder scan:** No TBDs, no "similar to Task N", all code blocks are complete.

**Type consistency:**
- `ExitReason` used consistently across all tasks (not `PositionMonitor.ExitReason`)
- `PreTradeResult` from `com.rj.engine.risk` used in Task 9 (TradingEngine) and Task 11 (RiskController)
- `PositionBook.add()` / `.remove()` / `.openPositions()` / `.isEmpty()` / `.values()` / `.hasOpenPosition()` consistent across Tasks 1, 4, 5, 6, 7, 8, 9, 10
- `RiskSessionState` methods consistent across Tasks 2, 3, 4, 5, 8, 9, 10, 11
- `ScheduledPositionManager.setExitHandler()` / `.setStrategyEvaluator()` consistent with Task 5 definition
