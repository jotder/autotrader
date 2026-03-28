package com.rj.fyers;

import com.rj.model.MultiOrderResult;
import com.rj.model.OrderResult;
import com.tts.in.model.FyersClass;
import com.tts.in.model.MultiLegModel;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.OrderType;
import com.tts.in.utilities.OrderValidity;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

import java.util.List;

public class FyersOrderPlacement {
    FyersClass fyersClass;

    public FyersOrderPlacement() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

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

    public OrderResult placeOrder(PlaceOrderModel model) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.PlaceOrder(model);
        if (tuple.Item2() != null) {
            System.out.println("PlaceOrder Error: " + tuple.Item2());
            return null;
        }
        return OrderResult.from(tuple.Item1());
    }

    // ── Convenience builders ──────────────────────────────────────────────────

    public MultiOrderResult placeMultipleOrders(List<PlaceOrderModel> models) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.PlaceMultipleOrders(models);
        if (tuple.Item2() != null) {
            System.out.println("PlaceMultipleOrders Error: " + tuple.Item2());
            return null;
        }
        return MultiOrderResult.from(tuple.Item1());
    }

    public MultiOrderResult placeMultiLegOrder(List<MultiLegModel> models) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.PlaceMultiLegOrder(models);
        if (tuple.Item2() != null) {
            System.out.println("PlaceMultiLegOrder Error: " + tuple.Item2());
            return null;
        }
        return MultiOrderResult.from(tuple.Item1());
    }
}
