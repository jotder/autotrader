package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An entry from GetOrderById / GetOrderByTag report APIs. */
public class ReportOrder {
    public final String symbol;
    public final String clientId;
    public final String idFyers;
    public final String exchOrdId;
    public final String description;
    public final String tradeDate;
    public final String transactionType;
    public final String productType;
    public final String status;
    public final String orderType;
    public final String ordSource;
    public final String rejectionReason;
    public final int exchange;
    public final int segment;
    public final int instrument;
    public final int qty;
    public final int tradedQty;
    public final boolean isSymbolActive;
    public final long tradeDateTimeEpoch;
    public final double tradedPrice;
    public final double limitPrice;

    private ReportOrder(String symbol, String clientId, String idFyers, String exchOrdId,
                        String description, String tradeDate, String transactionType,
                        String productType, String status, String orderType,
                        String ordSource, String rejectionReason,
                        int exchange, int segment, int instrument, int qty, int tradedQty,
                        boolean isSymbolActive, long tradeDateTimeEpoch,
                        double tradedPrice, double limitPrice) {
        this.symbol = symbol; this.clientId = clientId; this.idFyers = idFyers;
        this.exchOrdId = exchOrdId; this.description = description;
        this.tradeDate = tradeDate; this.transactionType = transactionType;
        this.productType = productType; this.status = status; this.orderType = orderType;
        this.ordSource = ordSource; this.rejectionReason = rejectionReason;
        this.exchange = exchange; this.segment = segment; this.instrument = instrument;
        this.qty = qty; this.tradedQty = tradedQty; this.isSymbolActive = isSymbolActive;
        this.tradeDateTimeEpoch = tradeDateTimeEpoch;
        this.tradedPrice = tradedPrice; this.limitPrice = limitPrice;
    }

    static ReportOrder from(JSONObject j) {
        return new ReportOrder(
            j.optString("symbol"), j.optString("clientId"), j.optString("id_fyers"),
            j.optString("exchOrdId"), j.optString("description"),
            j.optString("trade_date ").trim(), j.optString("transaction_type"),
            j.optString("product_type"), j.optString("status"), j.optString("ordertype"),
            j.optString("ord_source"), j.optString("rejection_reason"),
            j.optInt("exchange"), j.optInt("segment"), j.optInt("instrument"),
            j.optInt("qty"), j.optInt("tradedqty"),
            j.optBoolean("is_symbol_active"),
            j.optLong("trade_date_time"),
            j.optDouble("traded_price"), j.optDouble("limit_price")
        );
    }

    public static List<ReportOrder> listFrom(JSONObject json) {
        if (json == null) return Collections.emptyList();
        List<ReportOrder> result = new ArrayList<>();
        JSONArray data = json.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                result.add(from(data.getJSONObject(i)));
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override public String toString() {
        return "ReportOrder{symbol='" + symbol + "', status='" + status +
               "', qty=" + qty + ", tradedPrice=" + tradedPrice + "}";
    }
}
