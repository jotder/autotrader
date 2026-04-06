package com.rj.fyers;

import com.rj.config.ConfigManager;
import com.rj.model.ClientProfile;
import com.tts.in.model.FyersClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FyersClientFactory {

    static FyersClass fyersClass;

    /** Static reference set by Spring injection so static methods can use it. */
    private static ConfigManager configManagerInstance;

    @Autowired
    public void setConfigManager(ConfigManager configManager) {
        FyersClientFactory.configManagerInstance = configManager;
    }

    public FyersClientFactory() {
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
        ConfigManager conf = configManagerInstance;
        if (conf != null) {
            fyersClass.clientId = conf.getProperty("FYERS_APP_ID");
            fyersClass.accessToken = conf.getProperty("ACCESS_TOKEN");
        }
        return fyersClass;
    }

    public String generateToken(String accessToken) {
        if (configManagerInstance != null) {
            configManagerInstance.updateEnvProperty("ACCESS_TOKEN", accessToken);
        }
        return accessToken;
    }

    static public boolean isConnected() {
        ClientProfile profile = new FyersProfile().getProfile();
        return profile != null;
    }

    static public boolean connect(String accessToken) {
        if (configManagerInstance != null) {
            configManagerInstance.updateEnvProperty("ACCESS_TOKEN", accessToken);
        }
        ClientProfile profile = new FyersProfile().getProfile();
        return profile != null;
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
