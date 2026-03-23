package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A single OHLCV candle from the stock history API. */
public class Candle {
    public final long timestamp;
    public final double open;
    public final double high;
    public final double low;
    public final double close;
    public final long volume;

    private Candle(long timestamp, double open, double high, double low, double close, long volume) {
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    /** Parse from a candle array [epoch, open, high, low, close, volume]. */
    static Candle from(JSONArray arr) {
        return new Candle(
                arr.getLong(0),
                arr.getDouble(1),
                arr.getDouble(2),
                arr.getDouble(3),
                arr.getDouble(4),
                arr.getLong(5)
        );
    }

    /** Parse the full stock history response into a list of candles. */
    public static List<Candle> listFrom(JSONObject json) {
        if (json == null) return Collections.emptyList();
        List<Candle> candles = new ArrayList<>();
        JSONArray arr = json.optJSONArray("candles");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                candles.add(from(arr.getJSONArray(i)));
            }
        }
        return Collections.unmodifiableList(candles);
    }

    /** Factory for constructing a candle directly (e.g., aggregated from ticks). */
    public static Candle of(long timestamp, double open, double high,
                            double low, double close, long volume) {
        return new Candle(timestamp, open, high, low, close, volume);
    }

    @Override
    public String toString() {
        return "Candle{ts=" + timestamp + ", o=" + open + ", h=" + high +
                ", l=" + low + ", c=" + close + ", vol=" + volume + "}";
    }
}
