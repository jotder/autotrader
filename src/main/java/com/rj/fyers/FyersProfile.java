package com.rj.fyers;

import com.rj.model.ClientProfile;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

public class FyersProfile {
    FyersClass fyersClass;

    public FyersProfile() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    public ClientProfile getProfile() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
        Tuple<JSONObject, JSONObject> profileResponseTuple = fyersClass.GetProfile();

        if (profileResponseTuple.Item2() != null) {
            System.out.println("Profile login  Error: " + profileResponseTuple.Item2());
            return null;
        }

        JSONObject data = profileResponseTuple.Item1();
        if (data != null) {
            JSONObject profileJson = data.optJSONObject("data");
            if (profileJson == null)
                profileJson = data;

            return ClientProfile.from(profileJson);
        }
        return null;
    }
}
