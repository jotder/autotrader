package fyers;

import com.rj.model.FundSummary;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

public class FyersFund {
    FyersClass fyersClass;

    public FyersFund() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    public FundSummary getFunds() {
        Tuple<JSONObject, JSONObject> responseTuple = fyersClass.GetFunds();

        if (responseTuple.Item2() != null) {
            System.out.println("Fund Error: " + responseTuple.Item2());
            return null;
        }

        JSONObject data = responseTuple.Item1();
        JSONObject fundJson = data.optJSONObject("data");
        if (fundJson == null) {
            fundJson = data;
        }

        return FundSummary.from(fundJson);
    }
}
