package com.rj.model;

import org.json.JSONObject;

/** Response for single order placement, modification, or cancellation. */
public class OrderResult {
    public final int code;
    public final String status;
    public final String id;
    public final String message;

    private OrderResult(int code, String status, String id, String message) {
        this.code = code;
        this.status = status;
        this.id = id;
        this.message = message;
    }

    public static OrderResult from(JSONObject json) {
        if (json == null) return null;
        return new OrderResult(
                json.optInt("code"),
                json.optString("s"),
                json.optString("id"),
                json.optString("message")
        );
    }

    public boolean isOk() {
        return "ok".equals(status);
    }

    @Override
    public String toString() {
        return "OrderResult{code=" + code + ", id='" + id + "', message='" + message + "'}";
    }
}
