go agead
# Phase 3 — Engine Decomposition Design

## Goal

Split `RiskManager` and `PositionMonitor` into focused single-responsibility classes, and replace the `TradingEngine.create()` static factory with a Spring `@Bean` hand-written assembly method in `EngineConfiguration`. Every class has one job; circular dependencies are resolved explicitly with setters in the factory.

## Architecture

```
com.rj.engine.risk/
  RiskSessionState         (mutable daily state: kill switch, PnL, drawdown, anomaly)
  PreTradeGate             (preTradeCheck + sizing logic; reads RiskSessionState)

com.rj.engine/
  PositionBook             (ConcurrentHashMap<String, OpenPosition> wrapper)
  TickRiskProcessor        (EventHandler<TickEvent> — hot-path SL/TP/trailing stop)
  ScheduledPositionManager (1s scheduler — time exits, drawdown propagation, manual exit)
  TradingEngine            (constructor promoted; static factory deleted)
  ExitReason               (enum extracted from deleted PositionMonitor)
```

**Deleted:** `RiskManager`, `PositionMonitor`, `TradingEngine.create()`

**Unchanged:** `AnomalyDetector`, `HealthMonitor`, `BrokerCircuitBreaker`, `StrategyEvaluator`, `CandleService`, `OrderManager`, `TradeJournal`, all broker-layer classes.

---

## `RiskSessionState`

**Package:** `com.rj.engine.risk`

Owns all mutable daily trading state. No gate logic — pure state mutations and queries.

**Fields:**
- `AtomicBoolean killSwitchActive`
- `AtomicBoolean dailyProfitLocked`
- `AtomicBoolean anomalyMode`
- `volatile String anomalyReason`
- `volatile double dailyRealizedPnl`
- `volatile double peakSessionEquity`
- `volatile double currentOpenPnl`
- `ConcurrentHashMap<String, AtomicInteger> consecutiveLosses`
- `ConcurrentHashMap<String, StrategyRiskConfig> strategyRiskOverrides`

**Constructor:** `(RiskConfig riskConfig)`

**Mutation methods:**
- `recordClosedTrade(TradeRecord)` — updates `dailyRealizedPnl`, consecutive loss counter, profit lock flag
- `updateCurrentEquity(double totalOpenPnL)` — updates equity tracking, triggers kill switch on drawdown breach
- `triggerAnomaly(String reason)` — sets `anomalyMode`, `anomalyReason`
- `activateKillSwitch(String reason)` — sets `killSwitchActive`
- `resetKillSwitch()` — clears `killSwitchActive` and `anomalyMode`
- `applyStrategyRiskOverride(String strategyId, StrategyRiskConfig override)`
- `removeStrategyRiskOverride(String strategyId)`

**Query methods:** `isKillSwitchActive()`, `isAnomalyMode()`, `isDailyProfitLocked()`, `getDailyRealizedPnl()`, `getConsecutiveLosses(String strategyId)`, `getStrategyRiskOverride(String strategyId)`, `getAnomalyReason()`

---

## `PreTradeGate`

**Package:** `com.rj.engine.risk`

Owns all pre-trade gate logic and position sizing. Reads state from `RiskSessionState`; never mutates it directly (gate rejections are read-only; `recordClosedTrade` is called by `TradingEngine`, not here).

**Constructor:** `(RiskConfig riskConfig, RiskSessionState riskSessionState)`  
**Optional clock constructor:** `(RiskConfig, RiskSessionState, Supplier<ZonedDateTime> clock)` — for testing

**Fields:** `riskConfig`, `riskSessionState`, `clock`, `ConcurrentHashMap<String, TradeStrategyConfig> strategyConfigs`

**Methods:**
- `preTradeCheck(TradeSignal signal, Collection<OpenPosition> openPositions, double totalCapital)` → `PreTradeResult` — runs all 8 gates in order (kill switch, drawdown, profit lock, daily loss, time cutoff, consecutive losses, exposure, qty cap + sizing)
- `updateStrategyConfig(TradeStrategyConfig)` — registers YAML-loaded strategy config
- `applyStrategyRiskOverride(String, StrategyRiskConfig)` — delegates to `RiskSessionState`
- `removeStrategyRiskOverride(String)` — delegates to `RiskSessionState`

`PreTradeResult` record stays in `com.rj.engine.risk` (moved from inner class on `RiskManager`).

---

## `PositionBook`

**Package:** `com.rj.engine`

Thread-safe position store. No business logic, no threading.

**Fields:** `ConcurrentHashMap<String, OpenPosition> positions`

**Methods:**
- `add(OpenPosition)` — keyed by `correlationId`
- `remove(String correlationId)` → `OpenPosition` (returns removed, or null)
- `get(String correlationId)` → `OpenPosition`
- `openPositions()` → `Collection<OpenPosition>` (unmodifiable)
- `openPositionCount()` → `int`
- `hasOpenPosition(String symbol)` → `boolean`
- `isEmpty()` → `boolean`

---

## `TickRiskProcessor`

**Package:** `com.rj.engine`

Disruptor `EventHandler<TickEvent>` — hot path only. No scheduler, no manual control.

**Constructor:** `(PositionBook positionBook, RiskSessionState riskSessionState, RiskConfig riskConfig)`

**Setter injection (called from factory after `TradingEngine` is created):**
- `setExitHandler(BiConsumer<OpenPosition, ExitReason> exitHandler)`
- `setStrategyEvaluator(StrategyEvaluator strategyEvaluator)`

**`onEvent(TickEvent, long, boolean)`:**
1. Return if `positionBook.isEmpty()`
2. Return if `riskSessionState.isKillSwitchActive() && !riskSessionState.isAnomalyMode()`
3. For each position matching the tick symbol: check SL → check TP → update trailing stop

**Private `closePosition(OpenPosition, ExitReason)`:**
1. `positionBook.remove(pos.getCorrelationId())`
2. `exitHandler.accept(pos, reason)`
3. `strategyEvaluator.onPositionClosed(pos.getSymbol())` (if set)

---

## `ScheduledPositionManager`

**Package:** `com.rj.engine`

Owns the 1-second scheduled maintenance loop, time-based exits, manual exit, and `closeAllPositions`. Not on the hot path.

**Constructor:** `(PositionBook positionBook, RiskSessionState riskSessionState, RiskConfig riskConfig, TickStore tickStore)`

**Setter injection:**
- `setExitHandler(BiConsumer<OpenPosition, ExitReason> exitHandler)`
- `setStrategyEvaluator(StrategyEvaluator strategyEvaluator)`

**Methods:**
- `start()` / `stop()` — lifecycle for `ScheduledExecutorService`
- `requestManualExit(String correlationId)` — validates position exists, calls `closePosition()`
- `closeAllPositions(ExitReason)` → `int` — iterates `positionBook`, calls `closePosition()` for each

**`scheduledRiskMaintenance()` (1s task):**
1. Compute `totalOpenPnL` from `positionBook` + `tickStore`
2. Call `riskSessionState.updateCurrentEquity(totalOpenPnL)` — may activate kill switch
3. If `riskSessionState.isAnomalyMode()` and positions open → `closeAllPositions(ANOMALY_FLATTEN)`
4. If past `riskConfig.getMarketCloseTime()` → `closeAllPositions(FORCE_SQUAREOFF)`

**Private `closePosition(OpenPosition, ExitReason)`:** identical to `TickRiskProcessor.closePosition()` — 3 lines (remove from book, call exitHandler, notify strategyEvaluator).

---

## `ExitReason` enum

Extracted to standalone class `com.rj.engine.ExitReason` (previously inner enum on `PositionMonitor`):

```java
public enum ExitReason {
    STOP_LOSS, TAKE_PROFIT, TRAILING_STOP, FORCE_SQUAREOFF, MANUAL, ANOMALY_FLATTEN
}
```

All references to `PositionMonitor.ExitReason` updated to `ExitReason`.

---

## `TradingEngine` changes

**Constructor** (replaces private constructor + static factory):

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
              ConcurrentHashMap<String, TradeRecord> openRecords)
```

**Internal call-site changes:**

| Before | After |
|---|---|
| `riskManager.preTradeCheck(...)` | `preTradeGate.preTradeCheck(...)` |
| `riskManager.recordClosedTrade(record)` | `riskSessionState.recordClosedTrade(record)` |
| `riskManager.triggerAnomaly(reason)` | `riskSessionState.triggerAnomaly(reason)` |
| `positionMonitor.addPosition(pos)` | `positionBook.add(pos)` |
| `positionMonitor.openPositions()` | `positionBook.openPositions()` |
| `positionMonitor.closeAllPositions(reason)` | `scheduledPositionManager.closeAllPositions(reason)` |
| `positionMonitor.start()` / `.stop()` | `scheduledPositionManager.start()` / `.stop()` |
| `positionMonitor.requestManualExit(id)` | `scheduledPositionManager.requestManualExit(id)` |

**Getter changes:**

| Before | After |
|---|---|
| `getRiskManager()` | `getPreTradeGate()`, `getRiskSessionState()` |
| `getPositionMonitor()` | `getPositionBook()`, `getScheduledPositionManager()` |

**`flattenAll(String reason)`:**
```java
public int flattenAll(String reason) {
    riskSessionState.triggerAnomaly(reason);
    return scheduledPositionManager.closeAllPositions(ExitReason.ANOMALY_FLATTEN);
}
```

**`start()` / `stop()`:** replace `positionMonitor.start()/stop()` with `scheduledPositionManager.start()/stop()`.

---

## `EngineConfiguration` changes

**New `@Bean` methods (before the factory):**

```java
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
```

**`tradingEngine()` factory — assembly order:**

```java
@Bean
public TradingEngine tradingEngine(ConfigManager config, IOrderAdapter orderAdapter,
                                   PreTradeGate preTradeGate, RiskSessionState riskSessionState,
                                   PositionBook positionBook) {

    ExecutionMode mode = resolveMode(config.getProperty("APP_ENV"));
    TickStore tickStore = TickStore.getInstance();
    RiskConfig riskConfig = config.getRiskConfig();
    IOrderExecutor executor = createExecutor(mode, tickStore, orderAdapter);

    TradeJournal journal = new TradeJournal(mode);
    TickDisruptorEngine disruptor = new TickDisruptorEngine();

    // Step 1: construct components without callbacks
    ConcurrentHashMap<String, TradeRecord> openRecords = new ConcurrentHashMap<>();
    OrderTracker orderTracker = new OrderTracker(Duration.ofSeconds(30));
    LinkedBlockingQueue<CandleRecommendation> recQueue = new LinkedBlockingQueue<>(2048);

    TickRiskProcessor tickRiskProcessor =
            new TickRiskProcessor(positionBook, riskSessionState, riskConfig);
    ScheduledPositionManager scheduledPositionManager =
            new ScheduledPositionManager(positionBook, riskSessionState, riskConfig, tickStore);
    StrategyEvaluator se = new StrategyEvaluator(recQueue, null, riskConfig, positionBook);

    CandleService cs = new CandleService(tickStore, recQueue, config);
    FyersSocketListener socketListener = new FyersSocketListener(disruptor, null);
    OrderManager orderManager = new OrderManager(executor, orderTracker, journal);
    // Fix listener → orderManager (setter; replaces double-construction hack)
    socketListener.setOrderManager(orderManager);

    AnomalyDetector ad = new AnomalyDetector();
    CircuitBreakerConfig cbConfig =
            CircuitBreakerConfig.fromEnvironment(config::getProperty);
    BrokerCircuitBreaker cb = new BrokerCircuitBreaker(cbConfig, ad);
    if (executor instanceof LiveOrderExecutor loe) loe.setCircuitBreaker(cb);

    HealthMonitor hm = new HealthMonitor(tickStore, cs, se, scheduledPositionManager,
                                          config.getActiveSymbols());

    PositionReconciler reconciler = null;
    if (mode == ExecutionMode.LIVE) {
        reconciler = new PositionReconciler(orderAdapter, positionBook, openRecords, journal, riskConfig);
    }

    // Step 2: create TradingEngine
    TradingEngine engine = new TradingEngine(mode, executor, orderManager,
            preTradeGate, riskSessionState, positionBook,
            tickRiskProcessor, scheduledPositionManager,
            se, cs, ad, cb, hm, reconciler,
            journal, config, disruptor, socketListener, openRecords);

    // Step 3: wire callbacks (circular dep resolution)
    tickRiskProcessor.setExitHandler(engine::handleExit);
    tickRiskProcessor.setStrategyEvaluator(se);
    scheduledPositionManager.setExitHandler(engine::handleExit);
    scheduledPositionManager.setStrategyEvaluator(se);
    se.setSignalHandler(engine::handleSignal);
    se.setPositionManager(scheduledPositionManager); // replaces pm.setStrategyEvaluator(se)

    // Step 4: Disruptor handlers, OMS listener, anomaly init
    disruptor.addHandler(new TickStoreUpdater());
    disruptor.addHandler(tickRiskProcessor);
    orderTracker.addListener(engine);
    ad.initialize(riskSessionState, scheduledPositionManager, tickStore, journal, riskConfig);

    // Step 5: load strategies
    engine.loadYamlStrategies(cs, se, preTradeGate);
    engine.initializePluggableStrategies(se, preTradeGate);

    return engine;
}
```

**Removed `@Bean`s** that extracted sub-objects from TradingEngine (these now get their own `@Bean`s above):
- `tickDisruptorEngine(TradingEngine)` — `TickDisruptorEngine` is constructed in factory; expose via `engine.getDisruptorEngine()`
- `fyersSocketListener(TradingEngine)` — constructed in factory; expose via `engine.getSocketListener()`

**`PositionReconciler`** constructor updated: `positionMonitor` parameter → `PositionBook positionBook` (it only needs `openPositions()` for reconciliation).

---

## `StrategyEvaluator` changes

Add `setSignalHandler(Consumer<TradeSignal>)` setter — currently the handler is a required constructor parameter, which prevents construction before `TradingEngine` exists.

Add `setPositionManager(ScheduledPositionManager)` setter — replaces `PositionMonitor.setStrategyEvaluator(se)` back-pointer (currently `se.onPositionClosed()` is called by `PositionMonitor`; after split, called by both `TickRiskProcessor` and `ScheduledPositionManager` directly — no back-pointer needed on `StrategyEvaluator`).

Wait — `StrategyEvaluator.onPositionClosed()` is called by `closePosition()`. `StrategyEvaluator` does not hold a reference to `PositionMonitor`; it's the reverse. No setter needed on `StrategyEvaluator` for this. `setSignalHandler()` is the only new setter needed.

---

## `EngineController` changes

Two call-site updates:

```java
// Before:
engine.getPositionMonitor().openPositions()
engine.getPositionMonitor().requestManualExit(correlationId)

// After:
engine.getPositionBook().openPositions()
engine.getScheduledPositionManager().requestManualExit(correlationId)
```

---

## `AnomalyDetector` changes

`AnomalyDetector.initialize()` currently takes `RiskManager` and `PositionMonitor`. Updated signature:

```java
void initialize(RiskSessionState riskSessionState, ScheduledPositionManager scheduledPositionManager,
                TickStore tickStore, TradeJournal journal, RiskConfig riskConfig)
```

Internal updates:
- `riskManager.isAnomalyMode()` → `riskSessionState.isAnomalyMode()`
- `riskManager.getDailyRealizedPnl()` → `riskSessionState.getDailyRealizedPnl()`
- `riskManager.triggerAnomaly(reason)` → `riskSessionState.triggerAnomaly(reason)`
- `positionMonitor.closeAllPositions(PositionMonitor.ExitReason.ANOMALY_FLATTEN)` → `scheduledPositionManager.closeAllPositions(ExitReason.ANOMALY_FLATTEN)`

---

## `FyersSocketListener` changes

Currently the circular dep (`FyersSocketListener` → `OrderManager` ↔ `TradingEngine`) was solved by double-constructing `TradingEngine`. Replace with a `setOrderManager(OrderManager)` setter on `FyersSocketListener` — identical pattern to Phase 2 setter injection.

---

## Testing

### New tests

**`PreTradeGateTest`** (`@ExtendWith(MockitoExtension.class)`):
- Mock `RiskSessionState`; stub each kill condition; verify `preTradeCheck` returns rejection with correct reason
- Stub all gates passing; verify returned `PreTradeResult` has correct `quantity`, `stopLoss`, `takeProfit`

**`RiskSessionStateTest`** (`@ExtendWith(MockitoExtension.class)`):
- `recordClosedTrade` with losing trade: consecutive loss counter increments
- `recordClosedTrade` with winning trade: counter resets to 0
- `recordClosedTrade` reaching `maxDailyProfitInr`: `isDailyProfitLocked()` becomes true
- `updateCurrentEquity` with drawdown exceeding limit: `isKillSwitchActive()` becomes true

**`TickRiskProcessorTest`** (`@ExtendWith(MockitoExtension.class)`):
- SL hit: `exitHandler` called with `ExitReason.STOP_LOSS`
- TP hit: `exitHandler` called with `ExitReason.TAKE_PROFIT`
- Tick for symbol with no open position: no-op
- Kill switch active and anomaly mode false: `onEvent` returns early

### Updated tests

- **`RiskManagerStrategyOverrideTest`** → **deleted**; gate override behavior covered by `PreTradeGateTest`
- **`FnoRiskSizingTest`** → update import: `RiskManager` → `PreTradeGate`; update construction

### No tests

- `ScheduledPositionManager`: time-dependent scheduler; covered by manual testing
- `PositionBook`: pure ConcurrentHashMap wrapper; no logic to test

---

## Classes Deleted

| Class | Reason |
|---|---|
| `RiskManager` | Split into `RiskSessionState` + `PreTradeGate` |
| `PositionMonitor` | Split into `PositionBook` + `TickRiskProcessor` + `ScheduledPositionManager` |
| `TradingEngine.create()` | Static factory replaced by `EngineConfiguration.tradingEngine()` `@Bean` |

## Out of Scope

- Phase 4: Maven multi-module build
- `connected` flag timing in `FyersBrokerAdapter` (requires WebSocket `OnOpen` callback)
- Zerodha or any non-Fyers broker implementation
