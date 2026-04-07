# Phase 2 — Broker Abstraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Replace all ad-hoc Fyers SDK instantiation with three typed broker interfaces; eliminate `FyersClientFactory`; broker connection becomes user-triggered via `POST /api/connect`.

**Architecture:** Three interfaces in `com.rj.broker` (`IMarketDataAdapter`, `IOrderAdapter`, `ITickFeed`) are implemented by a single Spring `@Component` named `FyersBrokerAdapter`. Engine components inject whichever interface they need. `FyersClientFactory` is deleted. `TokenRefreshScheduler` is promoted to a Spring bean.

**Tech Stack:** Java 25, Spring Boot 3.4.4, Maven 3.9+, JUnit 5, Mockito, `@WebMvcTest`, `@ExtendWith(MockitoExtension.class)`

---

## File Map

**Create:**
- `src/main/java/com/rj/broker/IMarketDataAdapter.java`
- `src/main/java/com/rj/broker/IOrderAdapter.java`
- `src/main/java/com/rj/broker/ITickFeed.java`
- `src/main/java/com/rj/fyers/FyersBrokerAdapter.java`
- `src/test/java/com/rj/web/EngineControllerConnectTest.java`
- `src/test/java/com/rj/engine/CandleDownloaderTest.java`
- `src/test/java/com/rj/fyers/TokenRefreshSchedulerTest.java`

**Modify:**
- `src/main/java/com/rj/config/EngineConfiguration.java`
- `src/main/java/com/rj/config/EngineLifecycleManager.java`
- `src/main/java/com/rj/engine/CandleDownloader.java`
- `src/main/java/com/rj/engine/LiveOrderExecutor.java`
- `src/main/java/com/rj/engine/PositionReconciler.java`
- `src/main/java/com/rj/engine/TradingEngine.java`
- `src/main/java/com/rj/fyers/FyersSocketListener.java`
- `src/main/java/com/rj/fyers/TokenRefreshScheduler.java`
- `src/main/java/com/rj/web/EngineController.java`

**Delete:**
- `src/main/java/com/rj/fyers/FyersClientFactory.java`
- `src/main/java/com/rj/fyers/FyersDataApi.java`
- `src/main/java/com/rj/fyers/FyersOrderPlacement.java`
- `src/main/java/com/rj/fyers/FyersPositions.java`
- `src/main/java/com/rj/fyers/FyersOrders.java`
- `src/main/java/com/rj/fyers/FyersOrderManagement.java`

---

## Task 1: Define the three broker interfaces

**Files:**
- Create: `src/main/java/com/rj/broker/IMarketDataAdapter.java`
- Create: `src/main/java/com/rj/broker/IOrderAdapter.java`
- Create: `src/main/java/com/rj/broker/ITickFeed.java`

- [x] **Step 1: Create `IMarketDataAdapter`**

```java
// src/main/java/com/rj/broker/IMarketDataAdapter.java
package com.rj.broker;

import com.rj.model.Candle;
import com.rj.model.MarketDepthResult;
import com.rj.model.OptionChainResult;
import com.rj.model.QuoteEntry;
import com.tts.in.model.StockHistoryModel;

import java.util.List;

public interface IMarketDataAdapter {
    List<Candle> getHistory(StockHistoryModel request);
    List<QuoteEntry> getQuotes(String symbols);
    MarketDepthResult getMarketDepth(String symbol, int ohlcvFlag);
    OptionChainResult getOptionChain(String symbol, int strikeCount, String expiry);
}
```

- [x] **Step 2: Create `IOrderAdapter`**

```java
// src/main/java/com/rj/broker/IOrderAdapter.java
package com.rj.broker;

import com.rj.model.ApiResponse;
import com.rj.model.MultiOrderResult;
import com.rj.model.OrderEntry;
import com.rj.model.OrderResult;
import com.rj.model.PositionsSummary;
import com.tts.in.model.MultiLegModel;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.model.PositionConversionModel;

import java.util.List;

public interface IOrderAdapter {
    OrderResult placeOrder(PlaceOrderModel model);
    MultiOrderResult placeMultipleOrders(List<PlaceOrderModel> models);
    MultiOrderResult placeMultiLegOrder(List<MultiLegModel> models);
    OrderResult modifyOrder(PlaceOrderModel model);
    MultiOrderResult modifyMultipleOrders(List<PlaceOrderModel> models);
    OrderResult cancelOrder(String orderId);
    MultiOrderResult cancelMultipleOrders(List<String> orderIds);
    PositionsSummary getPositions();
    ApiResponse exitPositions(List<String> positionIds);
    ApiResponse exitPositionBySegmentSidePrdType(int[] sides, int[] segments, String[] products);
    ApiResponse convertPosition(PositionConversionModel model);
    List<OrderEntry> getOrders();
    List<OrderEntry> getOrderById(String orderId);
}
```

- [x] **Step 3: Create `ITickFeed`**

```java
// src/main/java/com/rj/broker/ITickFeed.java
package com.rj.broker;

import java.util.List;

public interface ITickFeed {
    void connect(String accessToken);
    void disconnect();
    void subscribe(List<String> symbols);
    void unsubscribe(List<String> symbols);
    boolean isConnected();
    void refreshToken(String newToken);
}
```

- [x] **Step 4: Compile to verify interfaces are valid**

Run: `mvn compile -pl . -q`
Expected: BUILD SUCCESS

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/rj/broker/
git commit -m "feat(broker): define IMarketDataAdapter, IOrderAdapter, ITickFeed interfaces"
```

---

## Task 2: Create `FyersBrokerAdapter`

**Files:**
- Create: `src/main/java/com/rj/fyers/FyersBrokerAdapter.java`

`FyersBrokerAdapter` is a Spring `@Component` implementing all three interfaces.
It uses **setter injection** for `FyersSocketListener` (to avoid a circular dependency:
`FyersBrokerAdapter` → `FyersSocketListener` → `TradingEngine` → `IOrderAdapter` = `FyersBrokerAdapter`).

`FyersOrderPlacement.marketOrder()` and `limitOrder()` are static builder helpers that
currently live in `FyersOrderPlacement`. Since `FyersOrderPlacement` will be deleted in Task 9,
move these helpers directly into `FyersBrokerAdapter` as `public static` methods — `LiveOrderExecutor`
calls them, and having them in the adapter keeps the call pattern the same.

- [x] **Step 1: Create `FyersBrokerAdapter.java`**

```java
// src/main/java/com/rj/fyers/FyersBrokerAdapter.java
package com.rj.fyers;

import com.rj.broker.IMarketDataAdapter;
import com.rj.broker.IOrderAdapter;
import com.rj.broker.ITickFeed;
import com.rj.config.ConfigManager;
import com.rj.model.*;
import com.tts.in.model.*;
import com.tts.in.utilities.OrderType;
import com.tts.in.utilities.OrderValidity;
import com.tts.in.utilities.Tuple;
import com.tts.in.websocket.FyersSocket;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class FyersBrokerAdapter implements IMarketDataAdapter, IOrderAdapter, ITickFeed {

    private static final Logger log = LoggerFactory.getLogger(FyersBrokerAdapter.class);

    private final FyersClass fyersClass;
    private final ConfigManager config;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // Setter-injected to break circular dep: FyersBrokerAdapter → FyersSocketListener
    //   → TradingEngine → IOrderAdapter = FyersBrokerAdapter
    private FyersSocketListener listener;
    private FyersSocket socket;

    public FyersBrokerAdapter(FyersClass fyersClass, ConfigManager config) {
        this.fyersClass = fyersClass;
        this.config = config;
    }

    @Autowired
    public void setSocketListener(FyersSocketListener listener) {
        this.listener = listener;
    }

    // ── ITickFeed ─────────────────────────────────────────────────────────────

    @Override
    public void connect(String accessToken) {
        fyersClass.clientId = config.getProperty("FYERS_APP_ID");
        fyersClass.accessToken = accessToken;
        config.updateEnvProperty("ACCESS_TOKEN", accessToken);

        socket = new FyersSocket(30);
        listener.socket = socket;
        // listener.fyersClass IS fyersClass — same FyersClass.getInstance() singleton
        listener.startWebSocket();
        listener.subscribe(Arrays.asList(config.getActiveSymbols()));

        connected.set(true);
        log.info("Broker connected (clientId={})", fyersClass.clientId);
    }

    @Override
    public void disconnect() {
        listener.close();
        connected.set(false);
        log.info("Broker disconnected");
    }

    @Override
    public void subscribe(List<String> symbols) {
        listener.subscribe(symbols);
    }

    @Override
    public void unsubscribe(List<String> symbols) {
        listener.unsubscribe(symbols);
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void refreshToken(String newToken) {
        fyersClass.accessToken = newToken;
        config.updateEnvProperty("ACCESS_TOKEN", newToken);
        log.info("Broker token refreshed");
    }

    // ── IMarketDataAdapter ────────────────────────────────────────────────────

    @Override
    public List<Candle> getHistory(StockHistoryModel request) {
        try {
            Tuple<JSONObject, JSONObject> tuple = fyersClass.GetStockHistory(request);
            if (tuple.Item2() != null) {
                log.error("GetStockHistory error: {}", tuple.Item2());
                return null;
            }
            return Candle.listFrom(tuple.Item1());
        } catch (Exception e) {
            log.error("GetStockHistory exception", e);
            return null;
        }
    }

    @Override
    public List<QuoteEntry> getQuotes(String symbols) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetStockQuotes(symbols);
        if (tuple.Item2() != null) {
            log.error("GetStockQuotes error: {}", tuple.Item2());
            return null;
        }
        return QuoteEntry.listFrom(tuple.Item1());
    }

    @Override
    public MarketDepthResult getMarketDepth(String symbol, int ohlcvFlag) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetMarketDepth(symbol, ohlcvFlag);
        if (tuple.Item2() != null) {
            log.error("GetMarketDepth error: {}", tuple.Item2());
            return null;
        }
        return MarketDepthResult.from(tuple.Item1());
    }

    @Override
    public OptionChainResult getOptionChain(String symbol, int strikeCount, String expiry) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetOptionChain(symbol, strikeCount, expiry);
        if (tuple.Item2() != null) {
            log.error("GetOptionChain error: {}", tuple.Item2());
            return null;
        }
        return OptionChainResult.from(tuple.Item1());
    }

    // ── IOrderAdapter ─────────────────────────────────────────────────────────

    @Override
    public OrderResult placeOrder(PlaceOrderModel model) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.PlaceOrder(model);
        if (tuple.Item2() != null) {
            log.error("PlaceOrder error: {}", tuple.Item2());
            return null;
        }
        return OrderResult.from(tuple.Item1());
    }

    @Override
    public MultiOrderResult placeMultipleOrders(List<PlaceOrderModel> models) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.PlaceMultipleOrders(models);
        if (tuple.Item2() != null) {
            log.error("PlaceMultipleOrders error: {}", tuple.Item2());
            return null;
        }
        return MultiOrderResult.from(tuple.Item1());
    }

    @Override
    public MultiOrderResult placeMultiLegOrder(List<MultiLegModel> models) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.PlaceMultiLegOrder(models);
        if (tuple.Item2() != null) {
            log.error("PlaceMultiLegOrder error: {}", tuple.Item2());
            return null;
        }
        return MultiOrderResult.from(tuple.Item1());
    }

    @Override
    public OrderResult modifyOrder(PlaceOrderModel model) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.ModifyOrder(model);
        if (tuple.Item2() != null) {
            log.error("ModifyOrder error: {}", tuple.Item2());
            return null;
        }
        return OrderResult.from(tuple.Item1());
    }

    @Override
    public MultiOrderResult modifyMultipleOrders(List<PlaceOrderModel> models) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.ModifyMultipleOrders(models);
        if (tuple.Item2() != null) {
            log.error("ModifyMultipleOrders error: {}", tuple.Item2());
            return null;
        }
        return MultiOrderResult.from(tuple.Item1());
    }

    @Override
    public OrderResult cancelOrder(String orderId) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.CancelOrder(orderId);
        if (tuple.Item2() != null) {
            log.error("CancelOrder error: {}", tuple.Item2());
            return null;
        }
        return OrderResult.from(tuple.Item1());
    }

    @Override
    public MultiOrderResult cancelMultipleOrders(List<String> orderIds) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.CancelMultipleOrders(orderIds);
        if (tuple.Item2() != null) {
            log.error("CancelMultipleOrders error: {}", tuple.Item2());
            return null;
        }
        return MultiOrderResult.from(tuple.Item1());
    }

    @Override
    public PositionsSummary getPositions() {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetPositions();
        if (tuple.Item2() != null) {
            log.error("GetPositions error: {}", tuple.Item2());
            return null;
        }
        return PositionsSummary.from(tuple.Item1());
    }

    @Override
    public ApiResponse exitPositions(List<String> positionIds) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.ExitPositions(positionIds);
        if (tuple.Item2() != null) {
            log.error("ExitPositions error: {}", tuple.Item2());
            return null;
        }
        return ApiResponse.from(tuple.Item1());
    }

    @Override
    public ApiResponse exitPositionBySegmentSidePrdType(int[] sides, int[] segments, String[] products) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.ExitPositionBySegmentSidePrdType(sides, segments, products);
        if (tuple.Item2() != null) {
            log.error("ExitPositionByFilter error: {}", tuple.Item2());
            return null;
        }
        return ApiResponse.from(tuple.Item1());
    }

    @Override
    public ApiResponse convertPosition(PositionConversionModel model) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.PositionConversion(model);
        if (tuple.Item2() != null) {
            log.error("PositionConversion error: {}", tuple.Item2());
            return null;
        }
        return ApiResponse.from(tuple.Item1());
    }

    @Override
    public List<OrderEntry> getOrders() {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetAllOrders();
        if (tuple.Item2() != null) {
            log.error("GetOrders error: {}", tuple.Item2());
            return Collections.emptyList();
        }
        JSONObject data = tuple.Item1();
        JSONObject ordersJson = data.optJSONObject("data");
        if (ordersJson == null) ordersJson = data;
        return OrderEntry.fromArray(ordersJson.optJSONArray("orderBook"));
    }

    @Override
    public List<OrderEntry> getOrderById(String orderId) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetOrderById(orderId);
        if (tuple.Item2() != null) {
            log.error("GetOrderById error: {}", tuple.Item2());
            return Collections.emptyList();
        }
        JSONObject data = tuple.Item1();
        JSONObject ordersJson = data.optJSONObject("data");
        if (ordersJson == null) ordersJson = data;
        return OrderEntry.fromArray(ordersJson.optJSONArray("orderBook"));
    }

    // ── Order model builders (moved from deleted FyersOrderPlacement) ─────────

    public static PlaceOrderModel marketOrder(String symbol, int qty, int side, String productType) {
        PlaceOrderModel m = new PlaceOrderModel();
        m.Symbol = symbol;
        m.Qty = qty;
        m.OrderType = OrderType.MarketOrder.getDescription();
        m.Side = side;
        m.ProductType = productType;
        m.LimitPrice = 0;
        m.StopPrice = 0;
        m.OrderValidity = OrderValidity.DAY;
        m.DisclosedQty = 0;
        m.OffLineOrder = false;
        m.StopLoss = 0;
        m.TakeProfit = 0;
        return m;
    }

    public static PlaceOrderModel limitOrder(String symbol, int qty, int side, String productType, double limitPrice) {
        PlaceOrderModel m = marketOrder(symbol, qty, side, productType);
        m.OrderType = OrderType.LimitOrder.getDescription();
        m.LimitPrice = limitPrice;
        return m;
    }
}
```

- [x] **Step 2: Compile — expect errors only because `FyersClass` bean and circular dep not yet resolved**

Run: `mvn compile -pl . -q 2>&1 | head -30`

`FyersBrokerAdapter` will fail to compile if `FyersClass` has no `@Bean` yet. That's expected and will be fixed in Task 3.

- [x] **Step 3: Commit the file (even with compile errors — next task resolves them)**

```bash
git add src/main/java/com/rj/fyers/FyersBrokerAdapter.java
git commit -m "feat(broker): add FyersBrokerAdapter implementing all three broker interfaces"
```

---

## Task 3: Wire `FyersClass` bean; fix `FyersSocketListener`; update `EngineConfiguration`

**Files:**
- Modify: `src/main/java/com/rj/fyers/FyersSocketListener.java` (line 28)
- Modify: `src/main/java/com/rj/config/EngineConfiguration.java`

`FyersSocketListener` currently has:
```java
public final FyersClass fyersClass = FyersClientFactory.getConfiguredInstance();
```
This must be changed to use `FyersClass.getInstance()` directly so it no longer depends on `FyersClientFactory`.
Both `FyersBrokerAdapter.fyersClass` and `FyersSocketListener.fyersClass` will be the same singleton
(`FyersClass.getInstance()`), so when `connect()` sets `fyersClass.accessToken`, the listener's reference
is already updated — no explicit copy needed.

`EngineConfiguration` needs:
1. A `@Bean FyersClass fyersClass()` so Spring can inject it into `FyersBrokerAdapter`
2. `tradingEngine()` updated to accept `IOrderAdapter` (passed into `TradingEngine.create()`)
3. `candleDownloader()` updated to accept `IMarketDataAdapter` (next task, but signature change here)

- [x] **Step 1: Fix `FyersSocketListener.fyersClass` field initializer**

In `src/main/java/com/rj/fyers/FyersSocketListener.java`, change line 28 from:
```java
public final FyersClass fyersClass = FyersClientFactory.getConfiguredInstance();
```
to:
```java
public final FyersClass fyersClass = FyersClass.getInstance();
```

Also remove the `import com.rj.fyers.FyersClientFactory;` if it exists (it doesn't — same package, no import needed), and update the import if `FyersClass` import is already present. Check the existing imports: `com.tts.in.model.FyersClass` is already imported at line 7. No import change needed.

- [x] **Step 2: Update `EngineConfiguration.java`**

Replace the full file content with:

```java
package com.rj.config;

import com.rj.broker.IOrderAdapter;
import com.rj.engine.BrokerCircuitBreaker;
import com.rj.engine.CandleDatabase;
import com.rj.engine.CandleDownloader;
import com.rj.engine.DownloadTracker;
import com.rj.engine.SymbolProfiler;
import com.rj.engine.TradingEngine;
import com.rj.engine.disruptor.TickDisruptorEngine;
import com.rj.fyers.FyersSocketListener;
import com.rj.model.TickStore;
import com.tts.in.model.FyersClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class EngineConfiguration {

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

    @Bean
    public TradingEngine tradingEngine(ConfigManager configManager, IOrderAdapter orderAdapter) {
        return TradingEngine.create(configManager, orderAdapter);
    }

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
            com.rj.broker.IMarketDataAdapter marketDataAdapter,
            CandleDatabase candleDatabase,
            BrokerCircuitBreaker circuitBreaker) {
        return new CandleDownloader(marketDataAdapter, candleDatabase, 500, circuitBreaker);
    }

    @Bean
    public DownloadTracker downloadTracker(CandleDownloader candleDownloader) {
        return new DownloadTracker(candleDownloader);
    }
}
```

- [x] **Step 3: Update `TradingEngine.create()` signature to accept `IOrderAdapter`**

In `src/main/java/com/rj/engine/TradingEngine.java`:

Add import at top:
```java
import com.rj.broker.IOrderAdapter;
```

Change line 80 from:
```java
public static TradingEngine create(ConfigManager config) {
```
to:
```java
public static TradingEngine create(ConfigManager config, IOrderAdapter orderAdapter) {
```

Change `createExecutor()` call at line 85 from:
```java
IOrderExecutor executor = createExecutor(mode, tickStore);
```
to:
```java
IOrderExecutor executor = createExecutor(mode, tickStore, orderAdapter);
```

Change `createExecutor()` method (around line 374) from:
```java
private static IOrderExecutor createExecutor(ExecutionMode mode, TickStore tickStore) {
    return switch (mode) {
        case LIVE -> new LiveOrderExecutor();
        case BACKTEST -> new BacktestOrderExecutor();
        default -> new PaperOrderExecutor(tickStore);
    };
}
```
to:
```java
private static IOrderExecutor createExecutor(ExecutionMode mode, TickStore tickStore, IOrderAdapter orderAdapter) {
    return switch (mode) {
        case LIVE -> new LiveOrderExecutor(orderAdapter);
        case BACKTEST -> new BacktestOrderExecutor();
        default -> new PaperOrderExecutor(tickStore);
    };
}
```

Change the `PositionReconciler` construction at line 143 from:
```java
engineFinal.positionReconciler = new PositionReconciler(
        new FyersPositions(), pm, engineFinal.openRecords, journal, riskCfg);
```
to:
```java
engineFinal.positionReconciler = new PositionReconciler(
        orderAdapter, pm, engineFinal.openRecords, journal, riskCfg);
```

Remove `import com.rj.fyers.FyersPositions;` from the imports (line 8).

- [x] **Step 4: Compile — expect errors only from `LiveOrderExecutor` and `PositionReconciler` (fixed in Tasks 6 & 7)**

Run: `mvn compile -pl . -q 2>&1 | grep "ERROR\|error:" | head -20`

Expected errors: `LiveOrderExecutor` constructor mismatch, `PositionReconciler` constructor mismatch, `CandleDownloader` constructor mismatch. All will be fixed in subsequent tasks.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/rj/fyers/FyersSocketListener.java \
        src/main/java/com/rj/config/EngineConfiguration.java \
        src/main/java/com/rj/engine/TradingEngine.java
git commit -m "feat(broker): wire FyersClass bean, update EngineConfiguration and TradingEngine.create() signature"
```

---

## Task 4: Add `POST /api/connect` endpoint (TDD)

**Files:**
- Create: `src/test/java/com/rj/web/EngineControllerConnectTest.java`
- Modify: `src/main/java/com/rj/web/EngineController.java`

- [x] **Step 1: Write the failing test**

```java
// src/test/java/com/rj/web/EngineControllerConnectTest.java
package com.rj.web;

import com.rj.broker.ITickFeed;
import com.rj.engine.TradingEngine;
import com.rj.fyers.TokenRefreshScheduler;
import com.rj.model.TickStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EngineController.class)
class EngineControllerConnectTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingEngine engine;

    @MockBean
    private TickStore tickStore;

    @MockBean
    private ITickFeed brokerFeed;

    @MockBean
    private TokenRefreshScheduler tokenRefreshScheduler;

    @Test
    void connect_withToken_returns200AndCallsConnect() throws Exception {
        mockMvc.perform(post("/api/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"test-token-abc\"}"))
                .andExpect(status().isOk());

        verify(brokerFeed).connect("test-token-abc");
    }

    @Test
    void connect_withBlankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/connect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `mvn test -pl . -Dtest=EngineControllerConnectTest -q 2>&1 | tail -20`
Expected: FAIL — `EngineController` has no `/api/connect` endpoint yet, and it doesn't inject `ITickFeed` or `TokenRefreshScheduler`.

- [x] **Step 3: Update `EngineController` to add the connect endpoint and inject the new dependencies**

Add these imports to `src/main/java/com/rj/web/EngineController.java`:
```java
import com.rj.broker.ITickFeed;
import com.rj.fyers.TokenRefreshScheduler;
```

Change the constructor from:
```java
public EngineController(TradingEngine engine, TickStore tickStore) {
    this.engine = engine;
    this.tickStore = tickStore;
}
```
to:
```java
private final ITickFeed brokerFeed;
private final TokenRefreshScheduler tokenRefreshScheduler;

public EngineController(TradingEngine engine, TickStore tickStore,
                        ITickFeed brokerFeed, TokenRefreshScheduler tokenRefreshScheduler) {
    this.engine = engine;
    this.tickStore = tickStore;
    this.brokerFeed = brokerFeed;
    this.tokenRefreshScheduler = tokenRefreshScheduler;
}
```

Add the `ConnectRequest` record and `POST /api/connect` endpoint at the end of the class (before the closing `}`):
```java
record ConnectRequest(String accessToken) {}

@PostMapping("/connect")
public ResponseEntity<Void> connect(@RequestBody ConnectRequest request) {
    if (request.accessToken() == null || request.accessToken().isBlank()) {
        return ResponseEntity.badRequest().build();
    }
    brokerFeed.connect(request.accessToken());
    return ResponseEntity.ok().build();
}
```

Also update the token status and token refresh endpoints to use the injected `tokenRefreshScheduler`
instead of `engine.getTokenRefreshScheduler()`.

Replace the existing `tokenStatus()` method:
```java
@GetMapping("/token/status")
public Map<String, Object> tokenStatus() {
    var result = new LinkedHashMap<String, Object>();
    result.put("autoRefreshRunning", tokenRefreshScheduler.isRunning());
    result.put("lastRefreshStatus", tokenRefreshScheduler.getLastRefreshStatus());
    result.put("lastRefreshTime", tokenRefreshScheduler.getLastRefreshTime());
    return result;
}
```

Replace the existing `tokenRefresh()` method:
```java
@PostMapping("/token/refresh")
public ActionResponse tokenRefresh() {
    boolean success = tokenRefreshScheduler.refreshNow();
    return new ActionResponse(success,
            success ? "Token refreshed successfully" : "Token refresh failed — check logs");
}
```

- [x] **Step 4: Run the test to verify it passes**

Run: `mvn test -pl . -Dtest=EngineControllerConnectTest -q 2>&1 | tail -10`
Expected: Tests run: 2, Failures: 0, Errors: 0

- [x] **Step 5: Commit**

```bash
git add src/test/java/com/rj/web/EngineControllerConnectTest.java \
        src/main/java/com/rj/web/EngineController.java
git commit -m "feat(broker): add POST /api/connect endpoint to EngineController"
```

---

## Task 5: Update `CandleDownloader` to use `IMarketDataAdapter` (TDD)

**Files:**
- Create: `src/test/java/com/rj/engine/CandleDownloaderTest.java`
- Modify: `src/main/java/com/rj/engine/CandleDownloader.java`

`CandleDownloader` currently takes `FyersDataApi dataApi` in its constructor and calls `dataApi.getStockHistory(model)`.
After this task it takes `IMarketDataAdapter marketDataAdapter` and calls `marketDataAdapter.getHistory(model)`.

- [x] **Step 1: Write the failing test**

```java
// src/test/java/com/rj/engine/CandleDownloaderTest.java
package com.rj.engine;

import com.rj.broker.IMarketDataAdapter;
import com.tts.in.model.StockHistoryModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandleDownloaderTest {

    @Mock
    private IMarketDataAdapter adapter;

    @Mock
    private CandleDatabase db;

    private CandleDownloader downloader;

    @BeforeEach
    void setup() {
        downloader = new CandleDownloader(adapter, db);
    }

    @Test
    void download_skipsDateAlreadyInDatabase() {
        LocalDate date = LocalDate.of(2026, 1, 2);
        when(db.exists("NSE:SBIN-EQ", date)).thenReturn(true);

        downloader.download("NSE:SBIN-EQ", date, date);

        verifyNoInteractions(adapter);
    }

    @Test
    void download_callsAdapterWithCorrectSymbolAndResolution() {
        LocalDate date = LocalDate.of(2026, 1, 2);
        when(db.exists("NSE:SBIN-EQ", date)).thenReturn(false);
        when(adapter.getHistory(any())).thenReturn(List.of());

        downloader.download("NSE:SBIN-EQ", date, date);

        ArgumentCaptor<StockHistoryModel> captor = ArgumentCaptor.forClass(StockHistoryModel.class);
        verify(adapter).getHistory(captor.capture());
        StockHistoryModel model = captor.getValue();
        assertThat(model.Symbol).isEqualTo("NSE:SBIN-EQ");
        assertThat(model.Resolution).isEqualTo("1");
        // fetchDay uses epoch format (DateFormat="0"), RangeFrom is a non-null epoch string
        assertThat(model.RangeFrom).isNotBlank();
        assertThat(model.DateFormat).isEqualTo("0");
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `mvn test -pl . -Dtest=CandleDownloaderTest -q 2>&1 | tail -10`
Expected: FAIL — `CandleDownloader` constructor still takes `FyersDataApi`.

- [x] **Step 3: Update `CandleDownloader` to use `IMarketDataAdapter`**

In `src/main/java/com/rj/engine/CandleDownloader.java`:

Replace the import:
```java
import com.rj.fyers.FyersDataApi;
```
with:
```java
import com.rj.broker.IMarketDataAdapter;
```

Change the field declaration from:
```java
private final FyersDataApi dataApi;
```
to:
```java
private final IMarketDataAdapter marketDataAdapter;
```

Change the two constructors from:
```java
public CandleDownloader(FyersDataApi dataApi, CandleDatabase db) {
    this(dataApi, db, 500, null);
}

public CandleDownloader(FyersDataApi dataApi, CandleDatabase db, long delayBetweenCallsMs,
                        BrokerCircuitBreaker circuitBreaker) {
    this.dataApi = dataApi;
    this.db = db;
    this.delayBetweenCallsMs = delayBetweenCallsMs;
    this.circuitBreaker = circuitBreaker;
}
```
to:
```java
public CandleDownloader(IMarketDataAdapter marketDataAdapter, CandleDatabase db) {
    this(marketDataAdapter, db, 500, null);
}

public CandleDownloader(IMarketDataAdapter marketDataAdapter, CandleDatabase db,
                        long delayBetweenCallsMs, BrokerCircuitBreaker circuitBreaker) {
    this.marketDataAdapter = marketDataAdapter;
    this.db = db;
    this.delayBetweenCallsMs = delayBetweenCallsMs;
    this.circuitBreaker = circuitBreaker;
}
```

In `fetchDay()` (lines 111-121), change both `dataApi.getStockHistory(model)` calls to `marketDataAdapter.getHistory(model)`:

```java
if (circuitBreaker != null) {
    return circuitBreaker.execute(() -> {
        try {
            return marketDataAdapter.getHistory(model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }, false);
}
return marketDataAdapter.getHistory(model);
```

- [x] **Step 4: Run the test to verify it passes**

Run: `mvn test -pl . -Dtest=CandleDownloaderTest -q 2>&1 | tail -10`
Expected: Tests run: 2, Failures: 0, Errors: 0

- [x] **Step 5: Commit**

```bash
git add src/test/java/com/rj/engine/CandleDownloaderTest.java \
        src/main/java/com/rj/engine/CandleDownloader.java
git commit -m "feat(broker): CandleDownloader now uses IMarketDataAdapter instead of FyersDataApi"
```

---

## Task 6: Update `LiveOrderExecutor` to use `IOrderAdapter`

**Files:**
- Modify: `src/main/java/com/rj/engine/LiveOrderExecutor.java`

`LiveOrderExecutor` currently stores `FyersOrderPlacement fyersOrders` and calls:
- `FyersOrderPlacement.marketOrder(...)` — static builder (moved to `FyersBrokerAdapter` in Task 2)
- `fyersOrders.placeOrder(model)` — becomes `orderAdapter.placeOrder(model)`

No new test — `LiveOrderExecutor` is tested by the engine integration; mocking at this level requires a
live `FyersClass` for `FyersOrderPlacement`. The adapter mock is tested via `EngineControllerConnectTest`.

- [x] **Step 1: Replace `FyersOrderPlacement` import and field with `IOrderAdapter`**

In `src/main/java/com/rj/engine/LiveOrderExecutor.java`:

Remove import:
```java
import com.rj.fyers.FyersOrderPlacement;
```

Add imports:
```java
import com.rj.broker.IOrderAdapter;
import com.rj.fyers.FyersBrokerAdapter;
```

Change field from:
```java
private final FyersOrderPlacement fyersOrders;
```
to:
```java
private final IOrderAdapter orderAdapter;
```

Change the two constructors from:
```java
public LiveOrderExecutor() {
    this(null);
}

public LiveOrderExecutor(BrokerCircuitBreaker circuitBreaker) {
    this.fyersOrders = new FyersOrderPlacement();
    this.circuitBreaker = circuitBreaker;
}
```
to:
```java
public LiveOrderExecutor(IOrderAdapter orderAdapter) {
    this(orderAdapter, null);
}

public LiveOrderExecutor(IOrderAdapter orderAdapter, BrokerCircuitBreaker circuitBreaker) {
    this.orderAdapter = orderAdapter;
    this.circuitBreaker = circuitBreaker;
}
```

Change `FyersOrderPlacement.marketOrder(...)` calls to `FyersBrokerAdapter.marketOrder(...)`.
There are two calls in `placeEntry()` and `placeExit()`:

In `placeEntry()`, change:
```java
PlaceOrderModel model = FyersOrderPlacement.marketOrder(
        signal.getSymbol(), quantity, side, productType);
```
to:
```java
PlaceOrderModel model = FyersBrokerAdapter.marketOrder(
        signal.getSymbol(), quantity, side, productType);
```

In `placeExit()`, change:
```java
PlaceOrderModel model = FyersOrderPlacement.marketOrder(
        position.getSymbol(), position.getQuantity(), side, productType);
```
to:
```java
PlaceOrderModel model = FyersBrokerAdapter.marketOrder(
        position.getSymbol(), position.getQuantity(), side, productType);
```

In `executeViaCircuitBreaker()`, change:
```java
OrderResult result = fyersOrders.placeOrder(model);
```
to:
```java
OrderResult result = orderAdapter.placeOrder(model);
```

In `directCall()` (line 159), change:
```java
OrderResult result = fyersOrders.placeOrder(model);
```
to:
```java
OrderResult result = orderAdapter.placeOrder(model);
```

Remove the `setAccessToken()` method (it was setting `FyersClass.getInstance().accessToken` directly — token is now managed by `FyersBrokerAdapter.refreshToken()`):
```java
// DELETE this method entirely:
public void setAccessToken(String token) {
    FyersClass.getInstance().accessToken = token;
    log.info("LiveOrderExecutor: access token updated");
}
```

Also remove the now-unused `import com.tts.in.model.FyersClass;`.

- [x] **Step 2: Compile to verify `LiveOrderExecutor` errors are resolved**

Run: `mvn compile -pl . -q 2>&1 | grep "LiveOrderExecutor\|error:" | head -20`
Expected: No errors mentioning `LiveOrderExecutor`.

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/rj/engine/LiveOrderExecutor.java
git commit -m "feat(broker): LiveOrderExecutor now uses IOrderAdapter instead of FyersOrderPlacement"
```

---

## Task 7: Update `PositionReconciler` to use `IOrderAdapter`

**Files:**
- Modify: `src/main/java/com/rj/engine/PositionReconciler.java`

`PositionReconciler` currently takes `FyersPositions fyersPositions` in its constructor and calls
`fyersPositions.getPositions()`. The `TradingEngine.create()` already passes `orderAdapter` to it
(changed in Task 3 Step 3).

- [x] **Step 1: Replace `FyersPositions` with `IOrderAdapter` in `PositionReconciler`**

In `src/main/java/com/rj/engine/PositionReconciler.java`:

Remove import:
```java
import com.rj.fyers.FyersPositions;
```

Add import:
```java
import com.rj.broker.IOrderAdapter;
```

Change field from:
```java
private final FyersPositions fyersPositions;
```
to:
```java
private final IOrderAdapter orderAdapter;
```

Change both constructors from:
```java
public PositionReconciler(FyersPositions fyersPositions,
                          PositionMonitor positionMonitor,
                          ConcurrentHashMap<String, TradeRecord> openRecords,
                          TradeJournal journal,
                          RiskConfig riskConfig) {
    this(fyersPositions, positionMonitor, openRecords, journal, riskConfig, null);
}

public PositionReconciler(FyersPositions fyersPositions,
                          PositionMonitor positionMonitor,
                          ConcurrentHashMap<String, TradeRecord> openRecords,
                          TradeJournal journal,
                          RiskConfig riskConfig,
                          BrokerCircuitBreaker circuitBreaker) {
    this.fyersPositions = fyersPositions;
    ...
}
```
to:
```java
public PositionReconciler(IOrderAdapter orderAdapter,
                          PositionMonitor positionMonitor,
                          ConcurrentHashMap<String, TradeRecord> openRecords,
                          TradeJournal journal,
                          RiskConfig riskConfig) {
    this(orderAdapter, positionMonitor, openRecords, journal, riskConfig, null);
}

public PositionReconciler(IOrderAdapter orderAdapter,
                          PositionMonitor positionMonitor,
                          ConcurrentHashMap<String, TradeRecord> openRecords,
                          TradeJournal journal,
                          RiskConfig riskConfig,
                          BrokerCircuitBreaker circuitBreaker) {
    this.orderAdapter = orderAdapter;
    ...
}
```

In `reconcile()` (lines 79-81), change both `fyersPositions.getPositions()` calls to `orderAdapter.getPositions()`:

```java
PositionsSummary brokerState = circuitBreaker != null
        ? circuitBreaker.execute(() -> orderAdapter.getPositions(), true)
        : orderAdapter.getPositions();
```

- [x] **Step 2: Compile to verify `PositionReconciler` errors are resolved**

Run: `mvn compile -pl . -q 2>&1 | grep "error:" | head -20`
Expected: No errors (or only errors from `TokenRefreshScheduler`, fixed next).

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/rj/engine/PositionReconciler.java
git commit -m "feat(broker): PositionReconciler now uses IOrderAdapter instead of FyersPositions"
```

---

## Task 8: Promote `TokenRefreshScheduler` to Spring `@Component` (TDD)

**Files:**
- Create: `src/test/java/com/rj/fyers/TokenRefreshSchedulerTest.java`
- Modify: `src/main/java/com/rj/fyers/TokenRefreshScheduler.java`
- Modify: `src/main/java/com/rj/config/EngineLifecycleManager.java`
- Modify: `src/main/java/com/rj/engine/TradingEngine.java`

`TokenRefreshScheduler` currently:
- Is NOT a Spring bean (instantiated by `TradingEngine.start()`)
- Calls `FyersClientFactory.refreshToken(newToken)` after successful refresh

After this task:
- Annotated `@Component`, injected by Spring
- Injects `ITickFeed` (the `FyersBrokerAdapter`) via constructor
- Calls `tickFeed.refreshToken(newToken)` instead of `FyersClientFactory.refreshToken()`
- `EngineLifecycleManager` starts and stops it
- `TradingEngine` no longer creates or stops it

- [x] **Step 1: Write the failing test**

```java
// src/test/java/com/rj/fyers/TokenRefreshSchedulerTest.java
package com.rj.fyers;

import com.rj.broker.ITickFeed;
import com.rj.config.ConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenRefreshSchedulerTest {

    @Mock
    private ITickFeed tickFeed;

    @Mock
    private ConfigManager config;

    @Test
    void refreshNow_withNoRefreshToken_returnsFalseAndDoesNotCallAdapter() {
        when(config.getProperty("REFRESH_TOKEN")).thenReturn(null);
        when(config.getProperty("FYERS_TOKEN_AUTO_REFRESH")).thenReturn("true");

        TokenRefreshScheduler scheduler = new TokenRefreshScheduler(config, tickFeed);
        boolean result = scheduler.refreshNow();

        verify(tickFeed, never()).refreshToken(any());
        assert !result;
    }

    @Test
    void refreshNow_withNoPin_returnsFalseAndDoesNotCallAdapter() {
        when(config.getProperty("REFRESH_TOKEN")).thenReturn("some-refresh-token");
        when(config.getProperty("FYERS_PIN")).thenReturn(null);

        TokenRefreshScheduler scheduler = new TokenRefreshScheduler(config, tickFeed);
        boolean result = scheduler.refreshNow();

        verify(tickFeed, never()).refreshToken(any());
        assert !result;
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `mvn test -pl . -Dtest=TokenRefreshSchedulerTest -q 2>&1 | tail -10`
Expected: FAIL — `TokenRefreshScheduler` constructor currently takes only `ConfigManager`.

- [x] **Step 3: Update `TokenRefreshScheduler`**

In `src/main/java/com/rj/fyers/TokenRefreshScheduler.java`:

Add imports:
```java
import com.rj.broker.ITickFeed;
import org.springframework.stereotype.Component;
```

Add `@Component` annotation before the class declaration:
```java
@Component
public class TokenRefreshScheduler {
```

Add `ITickFeed tickFeed` field:
```java
private final ITickFeed tickFeed;
```

Change the constructor from:
```java
public TokenRefreshScheduler(ConfigManager config) {
    this.config = config;
    this.tokenGenerator = new TokenGenerator();
}
```
to:
```java
public TokenRefreshScheduler(ConfigManager config, ITickFeed tickFeed) {
    this.config = config;
    this.tickFeed = tickFeed;
    this.tokenGenerator = new TokenGenerator();
}
```

In the `refreshNow()` method, find:
```java
if (newToken != null && !newToken.isBlank()) {
    // Force FyersClientFactory to pick up new token
    FyersClientFactory.refreshToken(newToken);
```
and change to:
```java
if (newToken != null && !newToken.isBlank()) {
    tickFeed.refreshToken(newToken);
```

Remove `import com.rj.fyers.FyersClientFactory;` (same package, no import, but any reference is gone).

- [x] **Step 4: Run the test to verify it passes**

Run: `mvn test -pl . -Dtest=TokenRefreshSchedulerTest -q 2>&1 | tail -10`
Expected: Tests run: 2, Failures: 0, Errors: 0

- [x] **Step 5: Update `TradingEngine` to remove `TokenRefreshScheduler` lifecycle**

In `src/main/java/com/rj/engine/TradingEngine.java`:

Remove the field declaration:
```java
private com.rj.fyers.TokenRefreshScheduler tokenRefreshScheduler;
```

In `start()` method, remove:
```java
tokenRefreshScheduler = new com.rj.fyers.TokenRefreshScheduler(config);
tokenRefreshScheduler.start();
```

In `stop()` method, remove:
```java
if (tokenRefreshScheduler != null) tokenRefreshScheduler.stop();
```

Remove the getter:
```java
public com.rj.fyers.TokenRefreshScheduler getTokenRefreshScheduler() { return tokenRefreshScheduler; }
```

- [x] **Step 6: Update `EngineLifecycleManager` to inject and start `TokenRefreshScheduler`**

In `src/main/java/com/rj/config/EngineLifecycleManager.java`:

Add import:
```java
import com.rj.fyers.TokenRefreshScheduler;
```

Change the constructor from:
```java
public EngineLifecycleManager(TradingEngine engine, FyersSocketListener socketListener, ConfigManager config) {
    this.engine = engine;
    this.socketListener = socketListener;
    this.config = config;
}
```
to:
```java
private final TokenRefreshScheduler tokenRefreshScheduler;

public EngineLifecycleManager(TradingEngine engine, FyersSocketListener socketListener,
                               ConfigManager config, TokenRefreshScheduler tokenRefreshScheduler) {
    this.engine = engine;
    this.socketListener = socketListener;
    this.config = config;
    this.tokenRefreshScheduler = tokenRefreshScheduler;
}
```

In `stop()`, add:
```java
tokenRefreshScheduler.stop();
```
(before `engine.stop()`)

- [x] **Step 7: Compile to verify all wiring is correct**

Run: `mvn compile -pl . -q 2>&1 | grep "error:" | head -20`
Expected: No errors.

- [x] **Step 8: Commit**

```bash
git add src/test/java/com/rj/fyers/TokenRefreshSchedulerTest.java \
        src/main/java/com/rj/fyers/TokenRefreshScheduler.java \
        src/main/java/com/rj/engine/TradingEngine.java \
        src/main/java/com/rj/config/EngineLifecycleManager.java
git commit -m "feat(broker): promote TokenRefreshScheduler to Spring @Component; uses ITickFeed.refreshToken()"
```

---

## Task 9: Update `EngineLifecycleManager`; remove broker connect logic

**Files:**
- Modify: `src/main/java/com/rj/config/EngineLifecycleManager.java`

`EngineLifecycleManager.start()` currently checks `FyersClientFactory.isConnected()` and
manually wires the socket. After this task: just calls `engine.start()`. Broker connection
happens only when the user calls `POST /api/connect`.

Also: `FyersSocketListener socketListener` field and the `ConfigManager config` field
are no longer needed once the broker connect logic is removed.

- [x] **Step 1: Simplify `EngineLifecycleManager.start()`**

Replace the full `src/main/java/com/rj/config/EngineLifecycleManager.java` with:

```java
package com.rj.config;

import com.rj.engine.TradingEngine;
import com.rj.fyers.TokenRefreshScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Starts the trading engine and token refresh scheduler after the Spring context is ready.
 * Broker connection is user-triggered via POST /api/connect — not automatic on startup.
 */
@Component
public class EngineLifecycleManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EngineLifecycleManager.class);

    private final TradingEngine engine;
    private final TokenRefreshScheduler tokenRefreshScheduler;
    private volatile boolean running = false;

    public EngineLifecycleManager(TradingEngine engine, TokenRefreshScheduler tokenRefreshScheduler) {
        this.engine = engine;
        this.tokenRefreshScheduler = tokenRefreshScheduler;
    }

    @Override
    public void start() {
        log.info("Starting PTA Backend via Spring lifecycle...");
        engine.start();
        tokenRefreshScheduler.start();
        running = true;
    }

    @Override
    public void stop() {
        log.info("Stopping TradingEngine via Spring lifecycle...");
        tokenRefreshScheduler.stop();
        engine.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
```

- [x] **Step 2: Compile**

Run: `mvn compile -pl . -q 2>&1 | grep "error:" | head -20`
Expected: No errors.

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/rj/config/EngineLifecycleManager.java
git commit -m "feat(broker): EngineLifecycleManager no longer does broker connect; engine starts in disconnected mode"
```

---

## Task 10: Delete old Fyers wrapper classes; final compile + test

**Files to delete:**
- `src/main/java/com/rj/fyers/FyersClientFactory.java`
- `src/main/java/com/rj/fyers/FyersDataApi.java`
- `src/main/java/com/rj/fyers/FyersOrderPlacement.java`
- `src/main/java/com/rj/fyers/FyersPositions.java`
- `src/main/java/com/rj/fyers/FyersOrders.java`
- `src/main/java/com/rj/fyers/FyersOrderManagement.java`

Also: `FyersProfile.java` uses `FyersClientFactory.getConfiguredInstance()` in a field.
Update it to use `FyersClass.getInstance()` directly before deleting `FyersClientFactory`.

- [x] **Step 1: Fix `FyersProfile` to remove `FyersClientFactory` dependency**

In `src/main/java/com/rj/fyers/FyersProfile.java`, change:
```java
public FyersProfile() {
    fyersClass = FyersClientFactory.getConfiguredInstance();
}

public ClientProfile getProfile() {
    fyersClass = FyersClientFactory.getConfiguredInstance();
    ...
```
to:
```java
public FyersProfile() {
    fyersClass = FyersClass.getInstance();
}

public ClientProfile getProfile() {
    ...
```
(remove the reassignment inside `getProfile()`; the constructor assignment is sufficient since `FyersClass.getInstance()` is a singleton)

- [x] **Step 2: Grep for any remaining `FyersClientFactory` references before deleting**

Run: `grep -r "FyersClientFactory" src/ --include="*.java" -l`
Expected output: Only `FyersClientFactory.java` itself. If any other files appear, fix them first.

- [x] **Step 3: Delete the six wrapper classes**

```bash
git rm src/main/java/com/rj/fyers/FyersClientFactory.java \
       src/main/java/com/rj/fyers/FyersDataApi.java \
       src/main/java/com/rj/fyers/FyersOrderPlacement.java \
       src/main/java/com/rj/fyers/FyersPositions.java \
       src/main/java/com/rj/fyers/FyersOrders.java \
       src/main/java/com/rj/fyers/FyersOrderManagement.java
```

- [x] **Step 4: Full compile**

Run: `mvn compile -pl . -q 2>&1 | grep "error:"`
Expected: No output (clean compile).

- [x] **Step 5: Run the full test suite**

Run: `mvn test -pl . -q 2>&1 | tail -20`

Expected to pass:
- `EngineControllerConnectTest` (2 tests)
- `CandleDownloaderTest` (2 tests)
- `TokenRefreshSchedulerTest` (2 tests)
- All existing Phase 1 tests (`StatusControllerTest`, `SymbolControllerTest`, `CandleControllerTest`, `EnvConfigPersistenceTest`, `StrategyControllerTest`)

Pre-existing failures that are NOT introduced by Phase 2 (ignore these):
- `CandleDatabaseTest`
- `FnoRiskSizingTest`
- `RiskManagerStrategyOverrideTest`
- `CandleAggregationTest`

If any OTHER test fails, investigate before proceeding.

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/rj/fyers/FyersProfile.java
git commit -m "feat(broker): delete FyersClientFactory and five Fyers wrapper classes; Phase 2 complete"
```

---

## Summary

After Task 10, the codebase has:
- Three broker interfaces in `com.rj.broker`
- `FyersBrokerAdapter` as the single Fyers implementation, Spring-managed
- No ad-hoc `new FyersDataApi()` / `new FyersOrderPlacement()` / `new FyersPositions()` anywhere
- `FyersClientFactory` deleted
- `TokenRefreshScheduler` Spring-managed, calls `ITickFeed.refreshToken()`
- `POST /api/connect` as the user-triggered broker connection entry point
- Engine starts in disconnected mode; broker connect is explicit user action
