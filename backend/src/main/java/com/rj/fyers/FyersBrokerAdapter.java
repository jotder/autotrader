package com.rj.fyers;

import com.rj.broker.IMarketDataAdapter;
import com.rj.broker.IOrderAdapter;
import com.rj.broker.ITickFeed;
import com.rj.config.ConfigManager;
import com.rj.model.ApiResponse;
import com.rj.model.Candle;
import com.rj.model.MarketDepthResult;
import com.rj.model.MultiOrderResult;
import com.rj.model.OptionChainResult;
import com.rj.model.OrderEntry;
import com.rj.model.OrderResult;
import com.rj.model.PositionsSummary;
import com.rj.model.QuoteEntry;
import com.tts.in.model.FyersClass;
import com.tts.in.model.MultiLegModel;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.model.StockHistoryModel;
import com.tts.in.model.PositionConversionModel;
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

    // Setter-injected to break circular dep:
    // FyersBrokerAdapter → FyersSocketListener (bean) → TradingEngine → IOrderAdapter = FyersBrokerAdapter
    private FyersSocketListener listener;
    private FyersSocket socket;

    public FyersBrokerAdapter(ConfigManager config) {
        this.fyersClass = config.getFyersClass();;
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

    // ── Order model builders (moved from FyersOrderPlacement which will be deleted) ──

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
