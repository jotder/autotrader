package com.rj.model;

import org.json.JSONObject;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Response for Get Price Alerts — a map of alert ID → alert entry. */
public class PriceAlertBook {
    /** Keyed by alert ID string. */
    public final Map<String, Entry> alerts;

    public static class Entry {
        public final String fyToken;
        public final String symbol;
        public final Alert alert;

        private Entry(String fyToken, String symbol, Alert alert) {
            this.fyToken = fyToken;
            this.symbol = symbol;
            this.alert = alert;
        }

        static Entry from(JSONObject j) {
            return new Entry(
                j.optString("fyToken"),
                j.optString("symbol"),
                Alert.from(j.optJSONObject("alert"))
            );
        }

        @Override public String toString() {
            return "Entry{symbol='" + symbol + "', alert=" + alert + "}";
        }
    }

    public static class Alert {
        public final String comparisonType;
        public final String condition;
        public final String name;
        public final String type;
        public final String notes;
        public final String triggeredAt;
        public final String createdAt;
        public final String modifiedAt;
        public final double value;
        public final int status;
        public final long triggeredEpoch;
        public final long createdEpoch;
        public final long modifiedEpoch;

        private Alert(String comparisonType, String condition, String name, String type,
                      String notes, String triggeredAt, String createdAt, String modifiedAt,
                      double value, int status, long triggeredEpoch, long createdEpoch, long modifiedEpoch) {
            this.comparisonType = comparisonType; this.condition = condition;
            this.name = name; this.type = type; this.notes = notes;
            this.triggeredAt = triggeredAt; this.createdAt = createdAt; this.modifiedAt = modifiedAt;
            this.value = value; this.status = status;
            this.triggeredEpoch = triggeredEpoch; this.createdEpoch = createdEpoch; this.modifiedEpoch = modifiedEpoch;
        }

        static Alert from(JSONObject j) {
            if (j == null) return null;
            return new Alert(
                j.optString("comparisonType"), j.optString("condition"),
                j.optString("name"), j.optString("type"), j.optString("notes"),
                j.optString("triggeredAt"), j.optString("createdAt"), j.optString("modifiedAt"),
                j.optDouble("value"), j.optInt("status"),
                j.optLong("triggeredEpoch"), j.optLong("createdEpoch"), j.optLong("modifiedEpoch")
            );
        }

        @Override public String toString() {
            return "Alert{name='" + name + "', condition='" + condition + "', value=" + value + ", status=" + status + "}";
        }
    }

    private PriceAlertBook(Map<String, Entry> alerts) {
        this.alerts = Collections.unmodifiableMap(alerts);
    }

    public static PriceAlertBook from(JSONObject json) {
        if (json == null) return null;
        Map<String, Entry> map = new LinkedHashMap<>();
        JSONObject data = json.optJSONObject("data");
        if (data != null) {
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String id = keys.next();
                map.put(id, Entry.from(data.optJSONObject(id)));
            }
        }
        return new PriceAlertBook(map);
    }

    @Override public String toString() {
        return "PriceAlertBook{count=" + alerts.size() + "}";
    }
}
