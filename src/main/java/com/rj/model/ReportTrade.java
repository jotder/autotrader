package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An entry from GetTradeByTag / GetTradeBook report APIs. */
public class ReportTrade {
    public final String symbol;
    public final String clientId;
    public final String description;
    public final String orderDateTime;
    public final String orderNumber;
    public final String tradeNumber;
    public final String exchangeOrderNo;
    public final String productType;
    public final int side;
    public final int exchange;
    public final int segment;
    public final int tradedQty;
    public final boolean isSymbolActive;
    public final double tradePrice;
    public final double tradeValue;

    private ReportTrade(String symbol, String clientId, String description, String orderDateTime,
                        String orderNumber, String tradeNumber, String exchangeOrderNo,
                        String productType, int side, int exchange, int segment,
                        int tradedQty, boolean isSymbolActive, double tradePrice, double tradeValue) {
        this.symbol = symbol; this.clientId = clientId; this.description = description;
        this.orderDateTime = orderDateTime; this.orderNumber = orderNumber;
        this.tradeNumber = tradeNumber; this.exchangeOrderNo = exchangeOrderNo;
        this.productType = productType; this.side = side;
        this.exchange = exchange; this.segment = segment;
        this.tradedQty = tradedQty; this.isSymbolActive = isSymbolActive;
        this.tradePrice = tradePrice; this.tradeValue = tradeValue;
    }

    static ReportTrade from(JSONObject j) {
        return new ReportTrade(
            j.optString("symbol"), j.optString("clientId"), j.optString("description"),
            j.optString("orderDateTime"), j.optString("orderNumber"),
            j.optString("tradeNumber"), j.optString("exchangeOrderNo"),
            j.optString("product_type"), j.optInt("side"),
            j.optInt("exchange"), j.optInt("segment"),
            j.optInt("traded_qty"), j.optBoolean("is_symbol_active"),
            j.optDouble("trade_price"), j.optDouble("trade_value")
        );
    }

    public static List<ReportTrade> listFrom(JSONObject json) {
        if (json == null) return Collections.emptyList();
        List<ReportTrade> result = new ArrayList<>();
        JSONArray data = json.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                result.add(from(data.getJSONObject(i)));
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override public String toString() {
        return "ReportTrade{symbol='" + symbol + "', tradedQty=" + tradedQty +
               ", tradePrice=" + tradePrice + ", tradeValue=" + tradeValue + "}";
    }
}
