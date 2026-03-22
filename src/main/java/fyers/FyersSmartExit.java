package fyers;

import com.rj.model.ApiResponse;
import com.rj.model.SmartExitTrigger;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

import java.util.List;

public class FyersSmartExit {
    FyersClass fyersClass;

    public FyersSmartExit() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    public SmartExitTrigger createSmartExit(JSONObject requestBody) {
        Tuple<JSONObject, JSONObject> t = fyersClass.CreateSmartExitTrigger(requestBody);
        return handleTrigger(t, "CreateSmartExit");
    }

    /** @param queryParams filter string or empty string for all */
    public List<SmartExitTrigger> getSmartExits(String queryParams) {
        Tuple<JSONObject, JSONObject> t = fyersClass.GetSmartExitTriggers(queryParams);
        if (t == null || t.Item2() != null) {
            System.out.println("GetSmartExits Error: " + (t == null ? "null" : t.Item2()));
            return null;
        }
        return SmartExitTrigger.listFrom(t.Item1());
    }

    public SmartExitTrigger updateSmartExit(JSONObject requestBody) {
        Tuple<JSONObject, JSONObject> t = fyersClass.UpdateSmartExitTrigger(requestBody);
        return handleTrigger(t, "UpdateSmartExit");
    }

    public ApiResponse activateDeactivateSmartExit(JSONObject requestBody) {
        Tuple<JSONObject, JSONObject> t = fyersClass.ActivateDeactivateSmartExitTrigger(requestBody);
        if (t == null || t.Item2() != null) {
            System.out.println("ActivateDeactivateSmartExit Error: " + (t == null ? "null" : t.Item2()));
            return null;
        }
        return ApiResponse.from(t.Item1());
    }

    private SmartExitTrigger handleTrigger(Tuple<JSONObject, JSONObject> t, String label) {
        if (t == null || t.Item2() != null) {
            System.out.println(label + " Error: " + (t == null ? "null" : t.Item2()));
            return null;
        }
        return SmartExitTrigger.dataFrom(t.Item1());
    }
}
