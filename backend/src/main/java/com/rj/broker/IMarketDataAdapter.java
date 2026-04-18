package com.rj.broker;

import com.rj.model.Candle;
import com.rj.model.MarketDepthResult;
import com.rj.model.OptionChainResult;
import com.rj.model.QuoteEntry;
import com.tts.in.model.StockHistoryModel;

import java.util.List;

public interface IMarketDataAdapter {
    List<Candle> getHistory(StockHistoryModel request);
    List<QuoteEntry> getQuotes(String symbols);
    MarketDepthResult getMarketDepth(String symbol, int ohlcvFlag);
    OptionChainResult getOptionChain(String symbol, int strikeCount, String expiry);
}
