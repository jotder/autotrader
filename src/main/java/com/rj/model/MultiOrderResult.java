package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Response for bulk order operations (place/modify/cancel multiple orders). */
public class MultiOrderResult {
    public final int code;
    public final String status;
    public final String message;
    public final List<Item> items;

    public static class Item {
        public final int statusCode;
        public final String statusDescription;
        public final OrderResult body;

        private Item(int statusCode, String statusDescription, OrderResult body) {
            this.statusCode = statusCode;
            this.statusDescription = statusDescription;
            this.body = body;
        }

        static Item from(JSONObject json) {
            return new Item(
                json.optInt("statusCode"),
                json.optString("statusDescription"),
                OrderResult.from(json.optJSONObject("body"))
            );
        }
    }

    private MultiOrderResult(int code, String status, String message, List<Item> items) {
        this.code = code;
        this.status = status;
        this.message = message;
        this.items = Collections.unmodifiableList(items);
    }

    public boolean isOk() { return "ok".equals(status); }

    public static MultiOrderResult from(JSONObject json) {
        if (json == null) return null;
        List<Item> items = new ArrayList<>();
        JSONArray data = json.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                items.add(Item.from(data.getJSONObject(i)));
            }
        }
        return new MultiOrderResult(
            json.optInt("code"),
            json.optString("s"),
            json.optString("message"),
            items
        );
    }

    @Override public String toString() {
        return "MultiOrderResult{code=" + code + ", items=" + items.size() + ", message='" + message + "'}";
    }
}
