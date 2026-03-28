package com.rj.fyers;

import com.tts.in.model.FyersClass;
import org.json.JSONObject;

public class FyersUser {
    FyersClass fyersClass;

    public FyersUser() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    /** Returns null on success (SDK returns null when logout succeeds), or error JSONObject. */
    public JSONObject logout() {
        JSONObject error = fyersClass.LogoutValidation();
        if (error != null) {
            System.out.println("Logout Error: " + error.toString(4));
        }
        return error;
    }
}
