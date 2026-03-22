package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MarketStatus {
    public final List<Segment> segments;

    public static class Segment {
        public final int segment;
        public final int exchange;
        public final String marketType;
        public final String status;

        private Segment(int segment, int exchange, String marketType, String status) {
            this.segment = segment;
            this.exchange = exchange;
            this.marketType = marketType;
            this.status = status;
        }

        static Segment from(JSONObject j) {
            return new Segment(
                j.optInt("segment"),
                j.optInt("exchange"),
                j.optString("market_type"),
                j.optString("status")
            );
        }

        public boolean isOpen() { return "OPEN".equals(status); }

        @Override public String toString() {
            return "Segment{segment=" + segment + ", exchange=" + exchange +
                   ", marketType='" + marketType + "', status='" + status + "'}";
        }
    }

    private MarketStatus(List<Segment> segments) {
        this.segments = Collections.unmodifiableList(segments);
    }

    public static MarketStatus from(JSONObject json) {
        if (json == null) return null;
        List<Segment> segments = new ArrayList<>();
        JSONArray arr = json.optJSONArray("marketStatus");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                segments.add(Segment.from(arr.getJSONObject(i)));
            }
        }
        return new MarketStatus(segments);
    }

    @Override public String toString() {
        return "MarketStatus{segments=" + segments + "}";
    }
}
