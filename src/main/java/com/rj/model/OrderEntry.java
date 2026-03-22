package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed representation of a single entry in the Fyers GetAllOrders() response.
 * Use {@link #fromArray(JSONArray)} to parse the full orderBook array.
 */
public class OrderEntry {

    private final String id;
    private final String idFyers;
    private final String symbol;
    private final String exSym;
    private final String description;
    private final String clientId;
    private final String pan;
    private final String fyToken;
    private final String exchOrdId;
    private final String orderNumStatus;
    private final String orderDateTime;
    private final String orderTag;
    private final String productType;
    private final String orderValidity;
    private final String source;
    private final String message;
    private final int type;
    private final int side;
    private final int slNo;
    private final int instrument;
    private final int segment;
    private final int exchange;
    private final int status;
    private final int qty;
    private final int filledQty;
    private final int remainingQuantity;
    private final int disclosedQty;
    private final boolean offlineOrder;
    private final double lp;
    private final double limitPrice;
    private final double tradedPrice;
    private final double stopPrice;
    private final double ch;
    private final double chp;
    private final double takeProfit;
    private final double stopLoss;

    private OrderEntry(Builder b) {
        this.id                 = b.id;
        this.idFyers            = b.idFyers;
        this.symbol             = b.symbol;
        this.exSym              = b.exSym;
        this.description        = b.description;
        this.clientId           = b.clientId;
        this.pan                = b.pan;
        this.fyToken            = b.fyToken;
        this.exchOrdId          = b.exchOrdId;
        this.orderNumStatus     = b.orderNumStatus;
        this.orderDateTime      = b.orderDateTime;
        this.orderTag           = b.orderTag;
        this.productType        = b.productType;
        this.orderValidity      = b.orderValidity;
        this.source             = b.source;
        this.message            = b.message;
        this.type               = b.type;
        this.side               = b.side;
        this.slNo               = b.slNo;
        this.instrument         = b.instrument;
        this.segment            = b.segment;
        this.exchange           = b.exchange;
        this.status             = b.status;
        this.qty                = b.qty;
        this.filledQty          = b.filledQty;
        this.remainingQuantity  = b.remainingQuantity;
        this.disclosedQty       = b.disclosedQty;
        this.offlineOrder       = b.offlineOrder;
        this.lp                 = b.lp;
        this.limitPrice         = b.limitPrice;
        this.tradedPrice        = b.tradedPrice;
        this.stopPrice          = b.stopPrice;
        this.ch                 = b.ch;
        this.chp                = b.chp;
        this.takeProfit         = b.takeProfit;
        this.stopLoss           = b.stopLoss;
    }

    public static OrderEntry from(JSONObject o) {
        return new Builder()
                .id               (o.optString("id", ""))
                .idFyers          (o.optString("id_fyers", ""))
                .symbol           (o.optString("symbol", ""))
                .exSym            (o.optString("ex_sym", ""))
                .description      (o.optString("description", ""))
                .clientId         (o.optString("clientId", ""))
                .pan              (o.optString("pan", ""))
                .fyToken          (o.optString("fyToken", ""))
                .exchOrdId        (o.optString("exchOrdId", ""))
                .orderNumStatus   (o.optString("orderNumStatus", ""))
                .orderDateTime    (o.optString("orderDateTime", ""))
                .orderTag         (o.optString("orderTag", ""))
                .productType      (o.optString("productType", ""))
                .orderValidity    (o.optString("orderValidity", ""))
                .source           (o.optString("source", ""))
                .message          (o.optString("message", ""))
                .type             (o.optInt("type", 0))
                .side             (o.optInt("side", 0))
                .slNo             (o.optInt("slNo", 0))
                .instrument       (o.optInt("instrument", 0))
                .segment          (o.optInt("segment", 0))
                .exchange         (o.optInt("exchange", 0))
                .status           (o.optInt("status", 0))
                .qty              (o.optInt("qty", 0))
                .filledQty        (o.optInt("filledQty", 0))
                .remainingQuantity(o.optInt("remainingQuantity", 0))
                .disclosedQty     (o.optInt("disclosedQty", 0))
                .offlineOrder     (o.optBoolean("offlineOrder", false))
                .lp               (o.optDouble("lp", 0))
                .limitPrice       (o.optDouble("limitPrice", 0))
                .tradedPrice      (o.optDouble("tradedPrice", 0))
                .stopPrice        (o.optDouble("stopPrice", 0))
                .ch               (o.optDouble("ch", 0))
                .chp              (o.optDouble("chp", 0))
                .takeProfit       (o.optDouble("takeProfit", 0))
                .stopLoss         (o.optDouble("stopLoss", 0))
                .build();
    }

    public static List<OrderEntry> fromArray(JSONArray arr) {
        List<OrderEntry> list = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(from(arr.getJSONObject(i)));
            }
        }
        return Collections.unmodifiableList(list);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getId()                { return id; }
    public String getIdFyers()           { return idFyers; }
    public String getSymbol()            { return symbol; }
    public String getExSym()             { return exSym; }
    public String getDescription()       { return description; }
    public String getClientId()          { return clientId; }
    public String getPan()               { return pan; }
    public String getFyToken()           { return fyToken; }
    public String getExchOrdId()         { return exchOrdId; }
    public String getOrderNumStatus()    { return orderNumStatus; }
    public String getOrderDateTime()     { return orderDateTime; }
    public String getOrderTag()          { return orderTag; }
    public String getProductType()       { return productType; }
    public String getOrderValidity()     { return orderValidity; }
    public String getSource()            { return source; }
    public String getMessage()           { return message; }
    public int getType()                 { return type; }
    public int getSide()                 { return side; }
    public int getSlNo()                 { return slNo; }
    public int getInstrument()           { return instrument; }
    public int getSegment()              { return segment; }
    public int getExchange()             { return exchange; }
    public int getStatus()               { return status; }
    public int getQty()                  { return qty; }
    public int getFilledQty()            { return filledQty; }
    public int getRemainingQuantity()    { return remainingQuantity; }
    public int getDisclosedQty()         { return disclosedQty; }
    public boolean isOfflineOrder()      { return offlineOrder; }
    public double getLp()                { return lp; }
    public double getLimitPrice()        { return limitPrice; }
    public double getTradedPrice()       { return tradedPrice; }
    public double getStopPrice()         { return stopPrice; }
    public double getCh()                { return ch; }
    public double getChp()               { return chp; }
    public double getTakeProfit()        { return takeProfit; }
    public double getStopLoss()          { return stopLoss; }

    @Override
    public String toString() {
        return "OrderEntry{" +
                "id='" + id + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", qty=" + qty +
                ", filledQty=" + filledQty +
                ", remainingQuantity=" + remainingQuantity +
                ", limitPrice=" + limitPrice +
                ", tradedPrice=" + tradedPrice +
                ", status=" + status +
                ", productType='" + productType + '\'' +
                ", orderTag='" + orderTag + '\'' +
                '}';
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {
        private String id = "", idFyers = "", symbol = "", exSym = "", description = "";
        private String clientId = "", pan = "", fyToken = "", exchOrdId = "", orderNumStatus = "";
        private String orderDateTime = "", orderTag = "", productType = "", orderValidity = "";
        private String source = "", message = "";
        private int type, side, slNo, instrument, segment, exchange, status;
        private int qty, filledQty, remainingQuantity, disclosedQty;
        private boolean offlineOrder;
        private double lp, limitPrice, tradedPrice, stopPrice, ch, chp, takeProfit, stopLoss;

        public Builder id(String v)                { id                = v; return this; }
        public Builder idFyers(String v)           { idFyers           = v; return this; }
        public Builder symbol(String v)            { symbol            = v; return this; }
        public Builder exSym(String v)             { exSym             = v; return this; }
        public Builder description(String v)       { description       = v; return this; }
        public Builder clientId(String v)          { clientId          = v; return this; }
        public Builder pan(String v)               { pan               = v; return this; }
        public Builder fyToken(String v)           { fyToken           = v; return this; }
        public Builder exchOrdId(String v)         { exchOrdId         = v; return this; }
        public Builder orderNumStatus(String v)    { orderNumStatus    = v; return this; }
        public Builder orderDateTime(String v)     { orderDateTime     = v; return this; }
        public Builder orderTag(String v)          { orderTag          = v; return this; }
        public Builder productType(String v)       { productType       = v; return this; }
        public Builder orderValidity(String v)     { orderValidity     = v; return this; }
        public Builder source(String v)            { source            = v; return this; }
        public Builder message(String v)           { message           = v; return this; }
        public Builder type(int v)                 { type              = v; return this; }
        public Builder side(int v)                 { side              = v; return this; }
        public Builder slNo(int v)                 { slNo              = v; return this; }
        public Builder instrument(int v)           { instrument        = v; return this; }
        public Builder segment(int v)              { segment           = v; return this; }
        public Builder exchange(int v)             { exchange          = v; return this; }
        public Builder status(int v)               { status            = v; return this; }
        public Builder qty(int v)                  { qty               = v; return this; }
        public Builder filledQty(int v)            { filledQty         = v; return this; }
        public Builder remainingQuantity(int v)    { remainingQuantity = v; return this; }
        public Builder disclosedQty(int v)         { disclosedQty      = v; return this; }
        public Builder offlineOrder(boolean v)     { offlineOrder      = v; return this; }
        public Builder lp(double v)                { lp                = v; return this; }
        public Builder limitPrice(double v)        { limitPrice        = v; return this; }
        public Builder tradedPrice(double v)       { tradedPrice       = v; return this; }
        public Builder stopPrice(double v)         { stopPrice         = v; return this; }
        public Builder ch(double v)                { ch                = v; return this; }
        public Builder chp(double v)               { chp               = v; return this; }
        public Builder takeProfit(double v)        { takeProfit        = v; return this; }
        public Builder stopLoss(double v)          { stopLoss          = v; return this; }

        public OrderEntry build() { return new OrderEntry(this); }
    }
}