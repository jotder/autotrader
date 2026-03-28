package com.rj.fyers;

import com.rj.config.ConfigManager;
import com.tts.in.model.FyersClass;

public class FyersClientFactory {

    static FyersClass fyersClass;

    private FyersClientFactory() {
    }

    /**
     * Returns the FyersClass singleton with clientId and accessToken set.
     * Re-applies credentials on every call so that a token refresh
     * (via {@link TokenRefreshScheduler}) is picked up without restart.
     */
    public static FyersClass getConfiguredInstance() {
        if (fyersClass == null) {
            fyersClass = FyersClass.getInstance();
        }
        ConfigManager conf = ConfigManager.getInstance();
        fyersClass.clientId = conf.getProperty("FYERS_APP_ID");
        fyersClass.accessToken = conf.getProperty("ACCESS_TOKEN");
        return fyersClass;
    }

    /**
     * Called by {@link TokenRefreshScheduler} after obtaining a new token.
     * Updates the in-memory singleton immediately (before the next API call).
     */
    public static void refreshToken(String newAccessToken) {
        if (fyersClass != null) {
            fyersClass.accessToken = newAccessToken;
        }
    }
}
