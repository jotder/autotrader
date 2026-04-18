package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed representation of a single entry in the Fyers GetTradeBook() response.
 * Use {@link #fromArray(JSONArray)} to parse the full tradeBook array.
 */
public class TradeEntry {

    private final String orderDateTime;
    private final String tradeNumber;
    private final String symbol;
    private final String clientId;
    private final String orderNumber;
    private final String fyToken;
    private final String exchangeOrderNo;
    private final String orderTag;
    private final String productType;
    private final int orderType;
    private final int side;
    private final int tradedQty;
    private final int segment;
    private final int exchange;
    private final long row;
    private final double tradeValue;
    private final double tradePrice;

    private TradeEntry(Builder b) {
        this.orderDateTime = b.orderDateTime;
        this.tradeNumber = b.tradeNumber;
        this.symbol = b.symbol;
        this.clientId = b.clientId;
        this.orderNumber = b.orderNumber;
        this.fyToken = b.fyToken;
        this.exchangeOrderNo = b.exchangeOrderNo;
        this.orderTag = b.orderTag;
        this.productType = b.productType;
        this.orderType = b.orderType;
        this.side = b.side;
        this.tradedQty = b.tradedQty;
        this.segment = b.segment;
        this.exchange = b.exchange;
        this.row = b.row;
        this.tradeValue = b.tradeValue;
        this.tradePrice = b.tradePrice;
    }

    public static TradeEntry from(JSONObject o) {
        return new Builder()
                .orderDateTime(o.optString("orderDateTime", ""))
                .tradeNumber(o.optString("tradeNumber", ""))
                .symbol(o.optString("symbol", ""))
                .clientId(o.optString("clientId", ""))
                .orderNumber(o.optString("orderNumber", ""))
                .fyToken(o.optString("fyToken", ""))
                .exchangeOrderNo(o.optString("exchangeOrderNo", ""))
                .orderTag(o.optString("orderTag", ""))
                .productType(o.optString("productType", ""))
                .orderType(o.optInt("orderType", 0))
                .side(o.optInt("side", 0))
                .tradedQty(o.optInt("tradedQty", 0))
                .segment(o.optInt("segment", 0))
                .exchange(o.optInt("exchange", 0))
                .row(o.optLong("row", 0))
                .tradeValue(o.optDouble("tradeValue", 0))
                .tradePrice(o.optDouble("tradePrice", 0))
                .build();
    }

    public static List<TradeEntry> fromArray(JSONArray arr) {
        List<TradeEntry> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(from(arr.getJSONObject(i)));
            }
        }
        return Collections.unmodifiableList(list);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getOrderDateTime() {
        return orderDateTime;
    }

    public String getTradeNumber() {
        return tradeNumber;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getClientId() {
        return clientId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getFyToken() {
        return fyToken;
    }

    public String getExchangeOrderNo() {
        return exchangeOrderNo;
    }

    public String getOrderTag() {
        return orderTag;
    }

    public String getProductType() {
        return productType;
    }

    public int getOrderType() {
        return orderType;
    }

    public int getSide() {
        return side;
    }

    public int getTradedQty() {
        return tradedQty;
    }

    public int getSegment() {
        return segment;
    }

    public int getExchange() {
        return exchange;
    }

    public long getRow() {
        return row;
    }

    public double getTradeValue() {
        return tradeValue;
    }

    public double getTradePrice() {
        return tradePrice;
    }

    @Override
    public String toString() {
        return "TradeEntry{" +
                "orderDateTime='" + orderDateTime + '\'' +
                ", tradeNumber='" + tradeNumber + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", tradedQty=" + tradedQty +
                ", tradePrice=" + tradePrice +
                ", tradeValue=" + tradeValue +
                ", productType='" + productType + '\'' +
                ", orderTag='" + orderTag + '\'' +
                '}';
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {
        private String orderDateTime = "", tradeNumber = "", symbol = "", clientId = "";
        private String orderNumber = "", fyToken = "", exchangeOrderNo = "";
        private String orderTag = "", productType = "";
        private int orderType, side, tradedQty, segment, exchange;
        private long row;
        private double tradeValue, tradePrice;

        public Builder orderDateTime(String v) {
            orderDateTime = v;
            return this;
        }

        public Builder tradeNumber(String v) {
            tradeNumber = v;
            return this;
        }

        public Builder symbol(String v) {
            symbol = v;
            return this;
        }

        public Builder clientId(String v) {
            clientId = v;
            return this;
        }

        public Builder orderNumber(String v) {
            orderNumber = v;
            return this;
        }

        public Builder fyToken(String v) {
            fyToken = v;
            return this;
        }

        public Builder exchangeOrderNo(String v) {
            exchangeOrderNo = v;
            return this;
        }

        public Builder orderTag(String v) {
            orderTag = v;
            return this;
        }

        public Builder productType(String v) {
            productType = v;
            return this;
        }

        public Builder orderType(int v) {
            orderType = v;
            return this;
        }

        public Builder side(int v) {
            side = v;
            return this;
        }

        public Builder tradedQty(int v) {
            tradedQty = v;
            return this;
        }

        public Builder segment(int v) {
            segment = v;
            return this;
        }

        public Builder exchange(int v) {
            exchange = v;
            return this;
        }

        public Builder row(long v) {
            row = v;
            return this;
        }

        public Builder tradeValue(double v) {
            tradeValue = v;
            return this;
        }

        public Builder tradePrice(double v) {
            tradePrice = v;
            return this;
        }

        public TradeEntry build() {
            return new TradeEntry(this);
        }
    }
}
