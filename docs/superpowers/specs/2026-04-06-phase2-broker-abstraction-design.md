# Phase 2 — Broker Abstraction Design

## Goal

Replace all ad-hoc Fyers SDK instantiation with a clean broker interface layer. Every engine component injects a typed interface; the Fyers implementation lives in one class. The static `FyersClientFactory` is eliminated. Broker connection is user-triggered via `POST /api/connect`.

## Architecture

Three interfaces in `com.rj.broker` define the broker contract. A single `FyersBrokerAdapter` in `com.rj.fyers` implements all three. Engine components inject whichever interface they need — no component imports anything from `com.rj.fyers` except `FyersBrokerAdapter` at the wiring layer (`EngineConfiguration`).

```
com.rj.broker/
  IMarketDataAdapter
  IOrderAdapter
  ITickFeed

com.rj.fyers/
  FyersBrokerAdapter   (implements all three + lifecycle)
  FyersSocketListener  (unchanged — handles tick dispatch to Disruptor)
  TokenGenerator       (unchanged — builds OAuth URL for manual login)
  FyersProfile         (unchanged)
  TokenRefreshScheduler (promoted to Spring @Component)
```

## Interfaces

### `IMarketDataAdapter`

```java
package com.rj.broker;

public interface IMarketDataAdapter {
    List<QuoteEntry> getQuotes(String... symbols);
    MarketDepthResult getMarketDepth(String symbol, int depth);
    List<Candle> getHistory(StockHistoryModel request);
    OptionChainResult getOptionChain(String symbol, int strikeCount, String expiry);
}
```

### `IOrderAdapter`

```java
package com.rj.broker;

public interface IOrderAdapter {
    String placeOrder(OrderRequest request);
    void cancelOrder(String orderId);
    void modifyOrder(String orderId, OrderModifyRequest request);
    List<Order> getOrders();
    List<Position> getPositions();
    List<Trade> getTrades();
}
```

### `ITickFeed`

```java
package com.rj.broker;

public interface ITickFeed {
    void connect(String accessToken);
    void disconnect();
    void subscribe(List<String> symbols);
    void unsubscribe(List<String> symbols);
    boolean isConnected();
}
```

## `FyersBrokerAdapter`

Single Spring `@Component` implementing `IMarketDataAdapter`, `IOrderAdapter`, and `ITickFeed`.

**Constructor dependencies:**
- `FyersClass fyersClass` — injected `@Bean` (initially unconfigured, configured on `connect()`)
- `FyersSocketListener listener` — injected `@Component` (handles tick dispatch to Disruptor)
- `ConfigManager config` — provides `clientId` (read from `.env` via existing config)

**Lifecycle:**
- `connect(String accessToken)` — reads `clientId` from `config`, sets both on `fyersClass`, creates `FyersSocket(30)`, attaches `listener`, starts socket
- `disconnect()` — stops socket, clears connection state
- `isConnected()` — returns current socket connection state

**Market data methods** — absorb all logic currently in `FyersDataApi`: build HTTP request, call Fyers REST API via `fyersClass`, deserialize response.

**Order methods** — absorb all logic currently in `FyersOrderPlacement`, `FyersPositions`, `FyersOrders`, `FyersOrderManagement`.

**Tick methods** — delegate subscribe/unsubscribe to `FyersSocket`; `connect()` owns socket lifecycle.

## `TokenRefreshScheduler` (promoted to Spring bean)

Currently manually instantiated inside `TradingEngine.start()`. Becomes a Spring `@Component`.

- Injects `FyersBrokerAdapter` (via `ITickFeed`)
- On each scheduled refresh: generates new token via `TokenGenerator`, calls `adapter.connect(newToken)`
- Scheduler starts only after initial `POST /api/connect` succeeds (guarded by `isConnected()` check)

## `EngineLifecycleManager` changes

**Before:**
```java
if (FyersClientFactory.isConnected()) {
    FyersClass f = FyersClientFactory.getConfiguredInstance();
    socketListener.socket = new FyersSocket(30);
    socketListener.fyersClass.clientId = f.clientId;
    socketListener.fyersClass.accessToken = f.accessToken;
    engine.start();
    running = true;
}
```

**After:**
```java
engine.start();   // starts in disconnected mode — no broker call
running = true;
```

Broker connection is triggered exclusively via `POST /api/connect`.

## `POST /api/connect` endpoint

Added to `EngineController` (or a new `ConnectionController` if `EngineController` grows large):

```java
@PostMapping("/api/connect")
ResponseEntity<Void> connect(@RequestBody ConnectRequest request) {
    brokerAdapter.connect(request.accessToken());
    return ResponseEntity.ok().build();
}
```

`ConnectRequest` is a simple record: `record ConnectRequest(String accessToken) {}`.

Once connected, the socket feed is live and ticks flow through `FyersSocketListener` → `TickDisruptorEngine` as before. Engine state transitions (e.g., a formal "goLive" mode) are Phase 3 scope.

## Classes Deleted

| Class | Reason |
|---|---|
| `FyersClientFactory` | Static factory eliminated; `FyersBrokerAdapter` owns `FyersClass` |
| `FyersDataApi` | Methods absorbed into `FyersBrokerAdapter` |
| `FyersOrderPlacement` | Methods absorbed into `FyersBrokerAdapter` |
| `FyersPositions` | Methods absorbed into `FyersBrokerAdapter` |
| `FyersOrders` | Methods absorbed into `FyersBrokerAdapter` |
| `FyersOrderManagement` | Methods absorbed into `FyersBrokerAdapter` |

## Call Site Changes

| Class | Before | After |
|---|---|---|
| `CandleDownloader` | `new FyersDataApi()` (ad-hoc) | inject `IMarketDataAdapter` |
| `LiveOrderExecutor` | `new FyersOrderPlacement()` (ad-hoc) | inject `IOrderAdapter` |
| `PositionReconciler` | `new FyersPositions()` (ad-hoc) | inject `IOrderAdapter` |
| `EngineLifecycleManager` | `FyersClientFactory.isConnected()` + socket wiring | no broker interaction on start |
| `TradingEngine.start()` | `new TokenRefreshScheduler(...)` | removed; Spring manages it |
| `EngineController` | no connect endpoint | `POST /api/connect` calls `adapter.connect(token)` |

## `EngineConfiguration` changes

- Add `@Bean FyersClass fyersClass()` — returns unconfigured `new FyersClass()`
- Add `@Bean FyersBrokerAdapter(FyersClass, FyersSocketListener)` — wires them
- Remove any remaining `FyersClientFactory` references

## Testing

- **`ConnectionControllerTest`** (`@WebMvcTest`) — `POST /api/connect` with valid token → 200, calls `adapter.connect()`; missing token → 400
- **`CandleDownloaderTest`** — `@MockBean IMarketDataAdapter`, verify `getHistory()` called with correct `StockHistoryModel`
- **`LiveOrderExecutorTest`** — `@MockBean IOrderAdapter`, verify `placeOrder()` called on trade signal
- **`PositionReconcilerTest`** — `@MockBean IOrderAdapter`, verify positions fetched and reconciled correctly
- **`TokenRefreshSchedulerTest`** — mock `ITickFeed`, verify `connect(newToken)` called on schedule

`FyersBrokerAdapter` itself: no unit tests. It is a thin delegation wrapper over the Fyers SDK which requires live credentials. Covered by manual integration testing against Fyers sandbox.

## Out of Scope

- Phase 3: `TradingEngine.create()` factory → Spring DI, `RiskManager` split, `PositionMonitor` split
- Phase 4: Maven multi-module build
- Zerodha or any non-Fyers broker implementation (interfaces are ready; implementation is future work)
