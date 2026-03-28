package com.rj.fyers;

import com.rj.model.HoldingsSummary;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

public class FyersHoldings {
    FyersClass fyersClass;

    public FyersHoldings() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    public HoldingsSummary getHoldings() {
        Tuple<JSONObject, JSONObject> holdingTuple = fyersClass.GetHoldings();

        if (holdingTuple.Item2() != null) {
            System.out.println("Holdings Error: " + holdingTuple.Item2());
            return null;
        }

        JSONObject data = holdingTuple.Item1();
        JSONObject holdingsJson = data.optJSONObject("data");
        if (holdingsJson == null) {
            holdingsJson = data;
        }

        return HoldingsSummary.from(holdingsJson);
    }
}
