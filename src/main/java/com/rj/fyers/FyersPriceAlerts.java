package com.rj.fyers;

import com.rj.model.ApiResponse;
import com.rj.model.PriceAlertBook;
import com.tts.in.model.FyersClass;
import com.tts.in.model.PriceAlertModel;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

public class FyersPriceAlerts {

    FyersClass fyersClass;

    public FyersPriceAlerts() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    public ApiResponse createPriceAlert(PriceAlertModel alert) {
        Tuple<JSONObject, JSONObject> t = fyersClass.CreatePriceAlert(alert);
        return handleApi(t, "CreatePriceAlert");
    }

    public PriceAlertBook getPriceAlerts() {
        Tuple<JSONObject, JSONObject> t = fyersClass.GetPriceAlerts();
        if (t == null || t.Item2() != null) {
            System.out.println("GetPriceAlerts Error: " + (t == null ? "null" : t.Item2()));
            return null;
        }
        return PriceAlertBook.from(t.Item1());
    }

    public ApiResponse updatePriceAlert(PriceAlertModel alert) {
        Tuple<JSONObject, JSONObject> t = fyersClass.UpdatePriceAlert(alert);
        return handleApi(t, "UpdatePriceAlert");
    }

    public ApiResponse deletePriceAlert(String alertId) {
        Tuple<JSONObject, JSONObject> t = fyersClass.DeletePriceAlert(alertId);
        return handleApi(t, "DeletePriceAlert");
    }

    public ApiResponse toggleAlert(String alertId) {
        Tuple<JSONObject, JSONObject> t = fyersClass.ToggleAlert(alertId);
        return handleApi(t, "ToggleAlert");
    }

    private ApiResponse handleApi(Tuple<JSONObject, JSONObject> t, String label) {
        if (t == null || t.Item2() != null) {
            System.out.println(label + " Error: " + (t == null ? "null" : t.Item2()));
            return null;
        }
        return ApiResponse.from(t.Item1());
    }
}
