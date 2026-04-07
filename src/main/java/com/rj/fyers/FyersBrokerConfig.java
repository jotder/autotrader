package com.rj.fyers;

import com.rj.model.MarketStatus;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

public class FyersBrokerConfig {
    FyersClass fyersClass;

    public FyersBrokerConfig() {
        fyersClass = FyersClass.getInstance();
    }

    public MarketStatus getMarketStatus() {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetMarketStatus();
        if (tuple.Item2() != null) {
            System.out.println("MarketStatus Error: " + tuple.Item2());
            return null;
        }
        return MarketStatus.from(tuple.Item1());
    }
}
