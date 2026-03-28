package com.rj.fyers;

import com.rj.model.TradeEntry;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

public class FyersTransactionInfo {
    FyersClass fyersClass;

    public FyersTransactionInfo() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    public List<TradeEntry> getTradeBook() {
        Tuple<JSONObject, JSONObject> tradeTuple = fyersClass.GetTradeBook();

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
        Tuple<JSONObject, JSONObject> tradeTuple = fyersClass.GetTradeByTag(tag);

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
