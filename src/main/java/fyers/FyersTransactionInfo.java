package fyers;

import com.rj.model.TradeEntry;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

public class FyersTransactionInfo {

    public List<TradeEntry> getTradeBook() {
        Tuple<JSONObject, JSONObject> tradeTuple = FyersClientFactory.getConfiguredInstance().GetTradeBook();

        if (tradeTuple.Item2() != null) {
            System.out.println("TradeBook Error: " + tradeTuple.Item2());
            return Collections.emptyList();
        }

        JSONObject data = tradeTuple.Item1();
        JSONObject tradeBookJson = data.optJSONObject("data");
        if (tradeBookJson == null) {
            tradeBookJson = data;
        }

        return TradeEntry.fromArray(tradeBookJson.optJSONArray("tradeBook"));
    }

    public List<TradeEntry> getTradeByTag(String tag) {
        Tuple<JSONObject, JSONObject> tradeTuple = FyersClientFactory.getConfiguredInstance().GetTradeByTag(tag);

        if (tradeTuple.Item2() != null) {
            System.out.println("TradeByTag Error: " + tradeTuple.Item2());
            return Collections.emptyList();
        }

        JSONObject data = tradeTuple.Item1();
        JSONObject tradeBookJson = data.optJSONObject("data");
        if (tradeBookJson == null) {
            tradeBookJson = data;
        }

        return TradeEntry.fromArray(tradeBookJson.optJSONArray("tradeBook"));
    }
}