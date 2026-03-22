package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SmartOrderBook {
    public final List<SmartOrder> orders;
    public final int count;
    public final int filterCount;

    public static class SmartOrder {
        public final String flowId;
        public final String symbol;
        public final String productType;
        public final int flowType;
        public final int side;
        public final int qty;
        public final int filledQty;
        public final int status;
        public final double limitPrice;
        public final long createdTime;
        public final long updatedTime;

        private SmartOrder(String flowId, String symbol, String productType, int flowType,
                           int side, int qty, int filledQty, int status,
                           double limitPrice, long createdTime, long updatedTime) {
            this.flowId = flowId; this.symbol = symbol; this.productType = productType;
            this.flowType = flowType; this.side = side; this.qty = qty;
            this.filledQty = filledQty; this.status = status;
            this.limitPrice = limitPrice; this.createdTime = createdTime; this.updatedTime = updatedTime;
        }

        static SmartOrder from(JSONObject j) {
            return new SmartOrder(
                j.optString("flowId"), j.optString("symbol"), j.optString("productType"),
                j.optInt("flowtype"), j.optInt("side"), j.optInt("qty"),
                j.optInt("filledQty"), j.optInt("status"),
                j.optDouble("limitPrice"), j.optLong("createdTime"), j.optLong("updatedTime")
            );
        }

        @Override public String toString() {
            return "SmartOrder{flowId='" + flowId + "', symbol='" + symbol + "', status=" + status + "}";
        }
    }

    private SmartOrderBook(List<SmartOrder> orders, int count, int filterCount) {
        this.orders = Collections.unmodifiableList(orders);
        this.count = count;
        this.filterCount = filterCount;
    }

    public static SmartOrderBook from(JSONObject json) {
        if (json == null) return null;
        List<SmartOrder> orders = new ArrayList<>();
        JSONArray arr = json.optJSONArray("orderBook");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                orders.add(SmartOrder.from(arr.getJSONObject(i)));
            }
        }
        return new SmartOrderBook(orders, json.optInt("count"), json.optInt("filterCount"));
    }

    @Override public String toString() {
        return "SmartOrderBook{count=" + count + ", filterCount=" + filterCount + "}";
    }
}
