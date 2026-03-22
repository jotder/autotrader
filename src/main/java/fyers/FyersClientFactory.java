package fyers;

import com.rj.config.ConfigManager;
import com.tts.in.model.FyersClass;

public class FyersClientFactory {

    static FyersClass fyersClass;

    private FyersClientFactory() {
    }

    /**
     * Returns the FyersClass singleton with clientId and accessToken set.
     * Applies credentials every call so that a token refresh is picked up
     * without restarting the application.
     */
    public static FyersClass getConfiguredInstance() {
        if (fyersClass == null || fyersClass.clientId == null || fyersClass.accessToken == null) {
            fyersClass = FyersClass.getInstance();
            ConfigManager conf = ConfigManager.getInstance();
            fyersClass.clientId = conf.getProperty("FYERS_APP_ID");
            fyersClass.accessToken = conf.getProperty("ACCESS_TOKEN");
        }
        return fyersClass;
    }
}