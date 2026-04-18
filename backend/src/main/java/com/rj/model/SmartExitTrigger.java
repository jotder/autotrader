package com.rj.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Represents a single Smart Exit trigger (used for create, update and list responses). */
public class SmartExitTrigger {
    public final String flowId;
    public final String name;
    public final double profitRate;
    public final double lossRate;
    public final int type;
    public final int waitTime;
    /** Only present in list response. */
    public final int activeStatus;
    public final int status;
    public final int exitStatus;
    public final long createdTimestamp;
    public final long updatedTimestamp;
    public final long expiryTimestamp;

    private SmartExitTrigger(String flowId, String name, double profitRate, double lossRate,
                             int type, int waitTime, int activeStatus, int status, int exitStatus,
                             long createdTimestamp, long updatedTimestamp, long expiryTimestamp) {
        this.flowId = flowId;
        this.name = name;
        this.profitRate = profitRate;
        this.lossRate = lossRate;
        this.type = type;
        this.waitTime = waitTime;
        this.activeStatus = activeStatus;
        this.status = status;
        this.exitStatus = exitStatus;
        this.createdTimestamp = createdTimestamp;
        this.updatedTimestamp = updatedTimestamp;
        this.expiryTimestamp = expiryTimestamp;
    }

    /** Parse a single trigger data object (from create/update "data" field). */
    public static SmartExitTrigger from(JSONObject j) {
        if (j == null) return null;
        // create uses "profit_rate"/"loss_rate"; update/list uses "profitRate"/"lossRate"
        double profitRate = j.has("profitRate") ? j.optDouble("profitRate") : j.optDouble("profit_rate");
        double lossRate = j.has("lossRate") ? j.optDouble("lossRate") : j.optDouble("loss_rate");
        return new SmartExitTrigger(
                j.optString("flowId"), j.optString("name"),
                profitRate, lossRate,
                j.optInt("type"), j.optInt("waitTime"),
                j.optInt("activeStatus"), j.optInt("status"), j.optInt("exitStatus"),
                j.optLong("createdTimestamp"), j.optLong("updatedTimestamp"), j.optLong("expiryTimestamp")
        );
    }

    /** Parse list response — extracts the "data" array. */
    public static List<SmartExitTrigger> listFrom(JSONObject json) {
        if (json == null) return Collections.emptyList();
        List<SmartExitTrigger> result = new ArrayList<>();
        JSONArray arr = json.optJSONArray("data");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                result.add(from(arr.getJSONObject(i)));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Parse create/update response — extracts the "data" object. */
    public static SmartExitTrigger dataFrom(JSONObject json) {
        if (json == null) return null;
        return from(json.optJSONObject("data"));
    }

    @Override
    public String toString() {
        return "SmartExitTrigger{flowId='" + flowId + "', name='" + name +
                "', profitRate=" + profitRate + ", lossRate=" + lossRate + "}";
    }
}
