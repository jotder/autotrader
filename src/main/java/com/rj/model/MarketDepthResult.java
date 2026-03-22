package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Market depth data keyed by symbol. */
public class MarketDepthResult {
    /** Keyed by symbol (e.g. "NSE:TCS-EQ"). */
    public final Map<String, Depth> depths;

    public static class DepthLevel {
        public final double price;
        public final int volume;
        public final int orders;

        private DepthLevel(double price, int volume, int orders) {
            this.price = price; this.volume = volume; this.orders = orders;
        }

        static DepthLevel from(JSONObject j) {
            return new DepthLevel(j.optDouble("price"), j.optInt("volume"), j.optInt("ord"));
        }

        @Override public String toString() {
            return "{price=" + price + ", vol=" + volume + ", ord=" + orders + "}";
        }
    }

    public static class Depth {
        public final long totalBuyQty;
        public final long totalSellQty;
        public final List<DepthLevel> bids;
        public final List<DepthLevel> asks;

        private Depth(long totalBuyQty, long totalSellQty, List<DepthLevel> bids, List<DepthLevel> asks) {
            this.totalBuyQty = totalBuyQty; this.totalSellQty = totalSellQty;
            this.bids = Collections.unmodifiableList(bids);
            this.asks = Collections.unmodifiableList(asks);
        }

        static Depth from(JSONObject j) {
            List<DepthLevel> bids = parseLevels(j.optJSONArray("bids"));
            List<DepthLevel> asks = parseLevels(j.optJSONArray("ask"));
            return new Depth(j.optLong("totalbuyqty"), j.optLong("totalsellqty"), bids, asks);
        }

        private static List<DepthLevel> parseLevels(JSONArray arr) {
            List<DepthLevel> levels = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    levels.add(DepthLevel.from(arr.getJSONObject(i)));
                }
            }
            return levels;
        }

        @Override public String toString() {
            return "Depth{buyQty=" + totalBuyQty + ", sellQty=" + totalSellQty +
                   ", bids=" + bids.size() + ", asks=" + asks.size() + "}";
        }
    }

    private MarketDepthResult(Map<String, Depth> depths) {
        this.depths = Collections.unmodifiableMap(depths);
    }

    public static MarketDepthResult from(JSONObject json) {
        if (json == null) return null;
        Map<String, Depth> map = new LinkedHashMap<>();
        JSONObject d = json.optJSONObject("d");
        if (d != null) {
            Iterator<String> keys = d.keys();
            while (keys.hasNext()) {
                String sym = keys.next();
                map.put(sym, Depth.from(d.getJSONObject(sym)));
            }
        }
        return new MarketDepthResult(map);
    }

    @Override public String toString() {
        return "MarketDepthResult{symbols=" + depths.keySet() + "}";
    }
}
