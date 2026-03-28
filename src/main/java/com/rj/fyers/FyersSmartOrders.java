package com.rj.fyers;

import com.rj.model.ApiResponse;
import com.rj.model.OrderResult;
import com.rj.model.SmartOrderBook;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

public class FyersSmartOrders {
    FyersClass fyersClass;

    public FyersSmartOrders() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    public OrderResult createSmartLimit(JSONObject requestBody) {
        Tuple<JSONObject, JSONObject> t = fyersClass.CreateSmartorderLimit(requestBody);
        return handleOrder(t, "CreateSmartLimit");
    }

    public OrderResult createSmartTrail(JSONObject requestBody) {
        Tuple<JSONObject, JSONObject> t = fyersClass.CreateSmartorderTrail(requestBody);
        return handleOrder(t, "CreateSmartTrail");
    }

    public OrderResult createSmartStep(JSONObject requestBody) {
        Tuple<JSONObject, JSONObject> t = fyersClass.CreateSmartorderStep(requestBody);
        return handleOrder(t, "CreateSmartStep");
    }

    public OrderResult createSmartSip(JSONObject requestBody) {
        Tuple<JSONObject, JSONObject> t = fyersClass.CreateSmartorderSip(requestBody);
        return handleOrder(t, "CreateSmartSip");
    }

    public ApiResponse modifySmartOrder(JSONObject requestBody) {
        Tuple<JSONObject, JSONObject> t = fyersClass.ModifySmartorder(requestBody);
        return handleApi(t, "ModifySmartOrder");
    }

    public ApiResponse cancelSmartOrder(JSONObject requestBody) {
        Tuple<JSONObject, JSONObject> t = fyersClass.CancelSmartorder(requestBody);
        return handleApi(t, "CancelSmartOrder");
    }

    public ApiResponse pauseSmartOrder(JSONObject requestBody) {
        Tuple<JSONObject, JSONObject> t = fyersClass.PauseSmartorder(requestBody);
        return handleApi(t, "PauseSmartOrder");
    }

    public ApiResponse resumeSmartOrder(JSONObject requestBody) {
        Tuple<JSONObject, JSONObject> t = fyersClass.ResumeSmartorder(requestBody);
        return handleApi(t, "ResumeSmartOrder");
    }

    /** @param queryParams filter string, e.g. "status=active", or empty string for all */
    public SmartOrderBook getSmartOrderBook(String queryParams) {
        Tuple<JSONObject, JSONObject> t = fyersClass.GetSmartorderBookWithFilter(queryParams);
        if (t == null || t.Item2() != null) {
            System.out.println("GetSmartOrderBook Error: " + (t == null ? "null" : t.Item2()));
            return null;
        }
        return SmartOrderBook.from(t.Item1());
    }

    private OrderResult handleOrder(Tuple<JSONObject, JSONObject> t, String label) {
        if (t == null || t.Item2() != null) {
            System.out.println(label + " Error: " + (t == null ? "null" : t.Item2()));
            return null;
        }
        return OrderResult.from(t.Item1());
    }

    private ApiResponse handleApi(Tuple<JSONObject, JSONObject> t, String label) {
        if (t == null || t.Item2() != null) {
            System.out.println(label + " Error: " + (t == null ? "null" : t.Item2()));
            return null;
        }
        return ApiResponse.from(t.Item1());
    }
}
