package com.rj.model;

import org.json.JSONObject;

import java.time.Instant;

/**
 * Represents a single real-time tick received from the Fyers WebSocket feed.
 * All price fields are in paise-scaled integers from the wire (e.g., 80150 = ₹801.50)
 * except ltp, open, high, low, close, avgTradePrice, bid, ask which are stored as double
 * after dividing by 100 for direct use in analysis.
 */
public class Tick {

    //region Identity
    private final String symbol;        // e.g. "NSE:SBIN-EQ"
    private final String type;          // e.g. "sf"
    private final Instant feedTime;     // exch_feed_time as Instant
    private final Instant lastTradedTime;
    //endregion

    //region Price
    private final double ltp;           // last traded price
    private final double open;
    private final double high;
    private final double low;
    private final double prevClose;
    private final double avgTradePrice;
    private final double bidPrice;
    private final double askPrice;
    private final double change;        // ch  (absolute)
    private final double changePct;     // chp (percentage)
    //endregion

    //region Volume / OI
    private final long volTradedToday;
    private final long lastTradedQty;
    private final long totBuyQty;
    private final long totSellQty;
    private final long openInterest;    // OI
    private final long turnover;        // in paise on the wire
    //endregion

    //region Depth
    private final int bidSize;
    private final int askSize;
    //endregion

    //region Limits & Ranges
    // Circuit limits (stored as received — raw integer paise values)
    private final long upperCircuit;    // upper_ckt
    private final long lowerCircuit;    // lower_ckt

    // 52-week range (raw integer paise values)
    private final long yearHigh;        // Yhigh
    private final long yearLow;         // Ylow
    //endregion

    private Tick(Builder b) {
        this.symbol = b.symbol;
        this.type = b.type;
        this.feedTime = b.feedTime;
        this.lastTradedTime = b.lastTradedTime;
        this.ltp = b.ltp;
        this.open = b.open;
        this.high = b.high;
        this.low = b.low;
        this.prevClose = b.prevClose;
        this.avgTradePrice = b.avgTradePrice;
        this.bidPrice = b.bidPrice;
        this.askPrice = b.askPrice;
        this.change = b.change;
        this.changePct = b.changePct;
        this.volTradedToday = b.volTradedToday;
        this.lastTradedQty = b.lastTradedQty;
        this.totBuyQty = b.totBuyQty;
        this.totSellQty = b.totSellQty;
        this.openInterest = b.openInterest;
        this.turnover = b.turnover;
        this.bidSize = b.bidSize;
        this.askSize = b.askSize;
        this.upperCircuit = b.upperCircuit;
        this.lowerCircuit = b.lowerCircuit;
        this.yearHigh = b.yearHigh;
        this.yearLow = b.yearLow;
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    public static Tick from(JSONObject json) {
        return new Builder()
                .symbol(json.optString("symbol", ""))
                .type(json.optString("type", ""))
                .feedTime(Instant.ofEpochSecond(json.optLong("exch_feed_time", 0)))
                .lastTradedTime(Instant.ofEpochSecond(json.optLong("last_traded_time", 0)))
                .ltp(json.optDouble("ltp", 0))
                .open(json.optDouble("open_price", 0))
                .high(json.optDouble("high_price", 0))
                .low(json.optDouble("low_price", 0))
                .prevClose(json.optDouble("prev_close_price", 0))
                .avgTradePrice(parseDouble(json.optString("avg_trade_price", "0")))
                .bidPrice(json.optDouble("bid_price", 0))
                .askPrice(json.optDouble("ask_price", 0))
                .change(parseDouble(json.optString("ch", "0")))
                .changePct(parseDouble(json.optString("chp", "0")))
                .volTradedToday(parseLong(json.optString("vol_traded_today", "0")))
                .lastTradedQty(json.optLong("last_traded_qty", 0))
                .totBuyQty(parseLong(json.optString("tot_buy_qty", "0")))
                .totSellQty(parseLong(json.optString("tot_sell_qty", "0")))
                .openInterest(parseLong(json.optString("OI", "0")))
                .turnover(parseLong(json.optString("turnover", "0")))
                .bidSize(json.optInt("bid_size", 0))
                .askSize(json.optInt("ask_size", 0))
                .upperCircuit(parseLong(json.optString("upper_ckt", "0")))
                .lowerCircuit(parseLong(json.optString("lower_ckt", "0")))
                .yearHigh(parseLong(json.optString("Yhigh", "0")))
                .yearLow(parseLong(json.optString("Ylow", "0")))
                .build();
    }

    // ── Derived helpers useful for analysis ──────────────────────────────────

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Spread between best ask and best bid. */
    public double spread() {
        return askPrice - bidPrice;
    }

    /** True if buy-side dominates (more buyers than sellers at touch). */
    public boolean isBuyPressure() {
        return totBuyQty > totSellQty;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** Percentage distance of LTP from the 52-week high (raw paise → %). */
    public double pctFromYearHigh() {
        if (yearHigh == 0) return 0;
        return ((yearHigh / 100.0) - ltp) / (yearHigh / 100.0) * 100.0;
    }

    /** Percentage distance of LTP from the 52-week low (raw paise → %). */
    public double pctFromYearLow() {
        if (ltp == 0) return 0;
        return (ltp - (yearLow / 100.0)) / ltp * 100.0;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getType() {
        return type;
    }

    public Instant getFeedTime() {
        return feedTime;
    }

    public Instant getLastTradedTime() {
        return lastTradedTime;
    }

    public double getLtp() {
        return ltp;
    }

    public double getOpen() {
        return open;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getPrevClose() {
        return prevClose;
    }

    public double getAvgTradePrice() {
        return avgTradePrice;
    }

    public double getBidPrice() {
        return bidPrice;
    }

    public double getAskPrice() {
        return askPrice;
    }

    public double getChange() {
        return change;
    }

    public double getChangePct() {
        return changePct;
    }

    public long getVolTradedToday() {
        return volTradedToday;
    }

    public long getLastTradedQty() {
        return lastTradedQty;
    }

    public long getTotBuyQty() {
        return totBuyQty;
    }

    public long getTotSellQty() {
        return totSellQty;
    }

    public long getOpenInterest() {
        return openInterest;
    }

    public long getTurnover() {
        return turnover;
    }

    public int getBidSize() {
        return bidSize;
    }

    public int getAskSize() {
        return askSize;
    }

    public long getUpperCircuit() {
        return upperCircuit;
    }

    public long getLowerCircuit() {
        return lowerCircuit;
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    public long getYearHigh() {
        return yearHigh;
    }

    public long getYearLow() {
        return yearLow;
    }

    @Override
    public String toString() {
        return "Tick{" +
                "symbol='" + symbol + '\'' +
                ", type='" + type + '\'' +
                ", feedTime=" + feedTime +
                ", lastTradedTime=" + lastTradedTime +
                ", ltp=" + ltp +
                ", open=" + open +
                ", high=" + high +
                ", low=" + low +
                ", prevClose=" + prevClose +
                ", avgTradePrice=" + avgTradePrice +
                ", bidPrice=" + bidPrice +
                ", askPrice=" + askPrice +
                ", change=" + change +
                ", changePct=" + changePct +
                ", volTradedToday=" + volTradedToday +
                ", lastTradedQty=" + lastTradedQty +
                ", totBuyQty=" + totBuyQty +
                ", totSellQty=" + totSellQty +
                ", openInterest=" + openInterest +
                ", turnover=" + turnover +
                ", bidSize=" + bidSize +
                ", askSize=" + askSize +
                ", upperCircuit=" + upperCircuit +
                ", lowerCircuit=" + lowerCircuit +
                ", yearHigh=" + yearHigh +
                ", yearLow=" + yearLow +
                '}';
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {
        private String symbol = "";
        private String type = "";
        private Instant feedTime = Instant.EPOCH;
        private Instant lastTradedTime = Instant.EPOCH;
        private double ltp, open, high, low, prevClose, avgTradePrice, bidPrice, askPrice, change, changePct;
        private long volTradedToday, lastTradedQty, totBuyQty, totSellQty, openInterest, turnover;
        private int bidSize, askSize;
        private long upperCircuit, lowerCircuit, yearHigh, yearLow;

        public Builder symbol(String v) {
            symbol = v;
            return this;
        }

        public Builder type(String v) {
            type = v;
            return this;
        }

        public Builder feedTime(Instant v) {
            feedTime = v;
            return this;
        }

        public Builder lastTradedTime(Instant v) {
            lastTradedTime = v;
            return this;
        }

        public Builder ltp(double v) {
            ltp = v;
            return this;
        }

        public Builder open(double v) {
            open = v;
            return this;
        }

        public Builder high(double v) {
            high = v;
            return this;
        }

        public Builder low(double v) {
            low = v;
            return this;
        }

        public Builder prevClose(double v) {
            prevClose = v;
            return this;
        }

        public Builder avgTradePrice(double v) {
            avgTradePrice = v;
            return this;
        }

        public Builder bidPrice(double v) {
            bidPrice = v;
            return this;
        }

        public Builder askPrice(double v) {
            askPrice = v;
            return this;
        }

        public Builder change(double v) {
            change = v;
            return this;
        }

        public Builder changePct(double v) {
            changePct = v;
            return this;
        }

        public Builder volTradedToday(long v) {
            volTradedToday = v;
            return this;
        }

        public Builder lastTradedQty(long v) {
            lastTradedQty = v;
            return this;
        }

        public Builder totBuyQty(long v) {
            totBuyQty = v;
            return this;
        }

        public Builder totSellQty(long v) {
            totSellQty = v;
            return this;
        }

        public Builder openInterest(long v) {
            openInterest = v;
            return this;
        }

        public Builder turnover(long v) {
            turnover = v;
            return this;
        }

        public Builder bidSize(int v) {
            bidSize = v;
            return this;
        }

        public Builder askSize(int v) {
            askSize = v;
            return this;
        }

        public Builder upperCircuit(long v) {
            upperCircuit = v;
            return this;
        }

        public Builder lowerCircuit(long v) {
            lowerCircuit = v;
            return this;
        }

        public Builder yearHigh(long v) {
            yearHigh = v;
            return this;
        }

        public Builder yearLow(long v) {
            yearLow = v;
            return this;
        }

        public Tick build() {
            return new Tick(this);
        }
    }
}
