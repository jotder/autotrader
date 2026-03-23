package com.rj.model;

import org.json.JSONObject;

/** Generic response for APIs that return only a status code and message (no data payload). */
public class ApiResponse {
    public final int code;
    public final String status;
    public final String message;

    private ApiResponse(int code, String status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public static ApiResponse from(JSONObject json) {
        if (json == null) return null;
        return new ApiResponse(
                json.optInt("code"),
                json.optString("s"),
                json.optString("message")
        );
    }

    public boolean isOk() {
        return "ok".equals(status);
    }

    @Override
    public String toString() {
        return "ApiResponse{code=" + code + ", status='" + status + "', message='" + message + "'}";
    }
}
