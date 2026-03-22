package fyers;

import com.rj.model.HoldingsSummary;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

public class FyersHoldings {

    public HoldingsSummary getHoldings() {
        Tuple<JSONObject, JSONObject> holdingTuple = FyersClientFactory.getConfiguredInstance().GetHoldings();

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
