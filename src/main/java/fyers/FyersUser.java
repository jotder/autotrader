package fyers;

import org.json.JSONObject;

public class FyersUser {

    /** Returns null on success (SDK returns null when logout succeeds), or error JSONObject. */
    public JSONObject logout() {
        JSONObject error = FyersClientFactory.getConfiguredInstance().LogoutValidation();
        if (error != null) {
            System.out.println("Logout Error: " + error.toString(4));
        }
        return error;
    }
}