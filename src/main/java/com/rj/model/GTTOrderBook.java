package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GTTOrderBook {
    public final List<GTTOrder> orders;

    public static class GTTOrder {
        public final String id;
        public final String idFyers;
        public final String clientId;
        public final String symbol;
        public final String symbolDesc;
        public final String symbolExch;
        public final String productType;
        public final String reportType;
        public final String createTime;
        public final String omsMsg;
        public final int exchange;
        public final int segment;
        public final int instrument;
        public final int ordStatus;
        public final int qty;
        public final int qty2;
        public final int tranSide;
        public final int gttOcoInd;
        public final int lotSize;
        public final int multiplier;
        public final int precision;
        public final long createTimeEpoch;
        public final double priceLimit;
        public final double price2Limit;
        public final double priceTrigger;
        public final double price2Trigger;
        public final double tickSize;
        public final double ltp;
        public final double ltpCh;
        public final double ltpChp;
        public final String fyToken;

        private GTTOrder(String id, String idFyers, String clientId, String symbol, String symbolDesc,
                         String symbolExch, String productType, String reportType, String createTime,
                         String omsMsg, int exchange, int segment, int instrument, int ordStatus,
                         int qty, int qty2, int tranSide, int gttOcoInd, int lotSize, int multiplier,
                         int precision, long createTimeEpoch, double priceLimit, double price2Limit,
                         double priceTrigger, double price2Trigger, double tickSize,
                         double ltp, double ltpCh, double ltpChp, String fyToken) {
            this.id = id; this.idFyers = idFyers; this.clientId = clientId;
            this.symbol = symbol; this.symbolDesc = symbolDesc; this.symbolExch = symbolExch;
            this.productType = productType; this.reportType = reportType; this.createTime = createTime;
            this.omsMsg = omsMsg; this.exchange = exchange; this.segment = segment;
            this.instrument = instrument; this.ordStatus = ordStatus;
            this.qty = qty; this.qty2 = qty2; this.tranSide = tranSide;
            this.gttOcoInd = gttOcoInd; this.lotSize = lotSize; this.multiplier = multiplier;
            this.precision = precision; this.createTimeEpoch = createTimeEpoch;
            this.priceLimit = priceLimit; this.price2Limit = price2Limit;
            this.priceTrigger = priceTrigger; this.price2Trigger = price2Trigger;
            this.tickSize = tickSize; this.ltp = ltp; this.ltpCh = ltpCh; this.ltpChp = ltpChp;
            this.fyToken = fyToken;
        }

        static GTTOrder from(JSONObject j) {
            return new GTTOrder(
                j.optString("id"), j.optString("id_fyers"), j.optString("clientId"),
                j.optString("symbol"), j.optString("symbol_desc"), j.optString("symbol_exch"),
                j.optString("product_type"), j.optString("report_type"), j.optString("create_time"),
                j.optString("oms_msg"),
                j.optInt("exchange"), j.optInt("segment"), j.optInt("instrument"),
                j.optInt("ord_status"), j.optInt("qty"), j.optInt("qty2"),
                j.optInt("tran_side"), j.optInt("gtt_oco_ind"), j.optInt("lot_size"),
                j.optInt("multiplier"), j.optInt("precision"),
                j.optLong("create_time_epoch"),
                j.optDouble("price_limit"), j.optDouble("price2_limit"),
                j.optDouble("price_trigger"), j.optDouble("price2_trigger"),
                j.optDouble("tick_size"), j.optDouble("ltp"),
                j.optDouble("ltp_ch"), j.optDouble("ltp_chp"),
                j.optString("fy_token")
            );
        }

        @Override public String toString() {
            return "GTTOrder{id='" + id + "', symbol='" + symbol + "', status='" + reportType + "'}";
        }
    }

    private GTTOrderBook(List<GTTOrder> orders) {
        this.orders = Collections.unmodifiableList(orders);
    }

    public static GTTOrderBook from(JSONObject json) {
        if (json == null) return null;
        List<GTTOrder> orders = new ArrayList<>();
        JSONArray arr = json.optJSONArray("orderBook");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                orders.add(GTTOrder.from(arr.getJSONObject(i)));
            }
        }
        return new GTTOrderBook(orders);
    }

    @Override public String toString() {
        return "GTTOrderBook{count=" + orders.size() + "}";
    }
}
