package com.rj.fyers;

import com.rj.model.Candle;
import com.rj.model.MarketDepthResult;
import com.rj.model.OptionChainResult;
import com.rj.model.QuoteEntry;
import com.tts.in.model.FyersClass;
import com.tts.in.model.StockHistoryModel;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

import java.util.List;

public class FyersDataApi {
    FyersClass fyersClass;

    public FyersDataApi() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    public List<Candle> getStockHistory(StockHistoryModel model) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetStockHistory(model);
        if (tuple.Item2() != null) {
            System.out.println("StockHistory Error: " + tuple.Item2());
            return null;
        }
        return Candle.listFrom(tuple.Item1());
    }

    public List<QuoteEntry> getStockQuotes(String symbols) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetStockQuotes(symbols);
        if (tuple.Item2() != null) {
            System.out.println("StockQuotes Error: " + tuple.Item2());
            return null;
        }
        return QuoteEntry.listFrom(tuple.Item1());
    }

    /** @param ohlcvFlag 0 = OHLCV + market depth, 1 = only OHLCV */
    public MarketDepthResult getMarketDepth(String symbol, int ohlcvFlag) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetMarketDepth(symbol, ohlcvFlag);
        if (tuple.Item2() != null) {
            System.out.println("MarketDepth Error: " + tuple.Item2());
            return null;
        }
        return MarketDepthResult.from(tuple.Item1());
    }

    /** @param timestamp expiry date as epoch string, or empty for nearest expiry */
    public OptionChainResult getOptionChain(String symbol, int strikeCount, String timestamp) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetOptionChain(symbol, strikeCount, timestamp);
        if (tuple.Item2() != null) {
            System.out.println("OptionChain Error: " + tuple.Item2());
            return null;
        }
        return OptionChainResult.from(tuple.Item1());
    }
}
