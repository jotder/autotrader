package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A single symbol quote from the GetStockQuotes API. */
public class QuoteEntry {
    public final String symbol;
    public final String fyToken;
    public final String description;
    public final String exchange;
    public final String shortName;
    public final double lastPrice;
    public final double change;
    public final double changePercent;
    public final double openPrice;
    public final double highPrice;
    public final double lowPrice;
    public final double prevClosePrice;
    public final double atp;
    public final double bid;
    public final double ask;
    public final double spread;
    public final long volume;
    public final long timestamp;

    private QuoteEntry(String symbol, String fyToken, String description, String exchange,
                       String shortName, double lastPrice, double change, double changePercent,
                       double openPrice, double highPrice, double lowPrice, double prevClosePrice,
                       double atp, double bid, double ask, double spread, long volume, long timestamp) {
        this.symbol = symbol;
        this.fyToken = fyToken;
        this.description = description;
        this.exchange = exchange;
        this.shortName = shortName;
        this.lastPrice = lastPrice;
        this.change = change;
        this.changePercent = changePercent;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.prevClosePrice = prevClosePrice;
        this.atp = atp;
        this.bid = bid;
        this.ask = ask;
        this.spread = spread;
        this.volume = volume;
        this.timestamp = timestamp;
    }

    static QuoteEntry from(JSONObject v) {
        return new QuoteEntry(
                v.optString("symbol"), v.optString("fyToken"), v.optString("description"),
                v.optString("exchange"), v.optString("short_name"),
                v.optDouble("lp"), v.optDouble("ch"), v.optDouble("chp"),
                v.optDouble("open_price"), v.optDouble("high_price"), v.optDouble("low_price"),
                v.optDouble("prev_close_price"), v.optDouble("atp"),
                v.optDouble("bid"), v.optDouble("ask"), v.optDouble("spread"),
                v.optLong("volume"), v.optLong("tt")
        );
    }

    /** Parse the full GetStockQuotes response. */
    public static List<QuoteEntry> listFrom(JSONObject json) {
        if (json == null) return Collections.emptyList();
        List<QuoteEntry> quotes = new ArrayList<>();
        JSONArray d = json.optJSONArray("d");
        if (d != null) {
            for (int i = 0; i < d.length(); i++) {
                JSONObject item = d.optJSONObject(i);
                if (item != null) {
                    JSONObject v = item.optJSONObject("v");
                    if (v != null) quotes.add(from(v));
                }
            }
        }
        return Collections.unmodifiableList(quotes);
    }

    @Override
    public String toString() {
        return "QuoteEntry{symbol='" + symbol + "', lp=" + lastPrice +
                ", ch=" + change + "(" + changePercent + "%)}";
    }
}
